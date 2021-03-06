import java.nio.file.{FileSystems, Files}
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}
import javax.servlet.ServletContext

import com.blinkbox.books.config.Configuration
import com.blinkboxbooks.resourceserver._
import com.typesafe.config.ConfigException
import com.typesafe.scalalogging.{Logger, StrictLogging}
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.scalatra.LifeCycle
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * The main class of the resource server application, that initialises everything
 * and tie things together.
 */
class ScalatraBootstrap extends LifeCycle with Configuration with StrictLogging {

  override def init(context: ServletContext) {

    // Root directory of resources to serve up.
    val dataDirStr = config.getString("data_dir")
    val dataDirectory = FileSystems.getDefault.getPath(dataDirStr)
    if (!Files.isDirectory(dataDirectory)) {
      throw new ConfigException.BadPath(dataDirStr, "Data directory parameter must point to a valid directory")
    }

    // Cache directory, where smaller versions of image files are stored.
    val cacheDirectory = FileSystems.getDefault.getPath(config.getString("cache.directory"))
    if (!Files.isDirectory(cacheDirectory)) {
      throw new ConfigException.BadPath("cache.directory", "Cache directory parameter must point to a valid directory")
    }

    // Maximum number of image processing threads.
    val numThreads = if (config.hasPath("threads.count"))
      config.getInt("threads.count")
    else
      Runtime.getRuntime.availableProcessors
    logger.info(s"Using $numThreads threads for image processing")

    // Logging levels.
    val infoThreshold = Duration(config.getInt("logging.perf.threshold.info"), MILLISECONDS)
    val warnThreshold = Duration(config.getInt("logging.perf.threshold.warn"), MILLISECONDS)
    val errorThreshold = Duration(config.getInt("logging.perf.threshold.error"), MILLISECONDS)

    // Not making this configurable at the moment, as this should only change after careful consideration!
    val cachedFileSizes = Set(400, 900)

    // Create a custom thread pool with a limited number of threads, a limited size queue, and
    // custom thread names.
    // Default value to 0, which means that cache updating is disabled.
    val cacheingThreadCount =
      if (config.hasPath("cache.threads.count"))
        config.getInt("cache.threads.count")
      else 0
    logger.info(s"Using $cacheingThreadCount threads for background resizing of images for cache")

    val cacheWritingEnabled = cacheingThreadCount > 0
    logger.info(s"Caching of images is ${if (cacheWritingEnabled) "" else "not "}enabled")

    val maximumCacheQueueSize = config.getInt("cache.queue.limit")
    val threadFactory = new BasicThreadFactory.Builder().namingPattern("image-caching-%d").build()
    val threadPool = new ThreadPoolExecutor(cacheingThreadCount, cacheingThreadCount.max(1), 0L, TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue[Runnable](maximumCacheQueueSize), threadFactory, new ThreadPoolExecutor.AbortPolicy())

    val cacheQueueLogger = Logger(LoggerFactory getLogger ResourceServlet.getClass.getName)
    val cacheingExecutionContext = ExecutionContext.fromExecutor(threadPool)

    // The object that looks up files on the file system and in ePubs.
    val fileResolver = new EpubEnabledFileResolver(dataDirectory)

    // Create and mount the resource servlet.
    context.mount(ResourceServlet(fileResolver,
      new FileSystemImageCache(cacheDirectory, cachedFileSizes, fileResolver, cacheWritingEnabled),
      cacheingExecutionContext, numThreads,
      infoThreshold, warnThreshold, errorThreshold), "/*")
  }

}
