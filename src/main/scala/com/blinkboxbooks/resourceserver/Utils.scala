package com.blinkboxbooks.resourceserver

import org.apache.commons.codec.digest.DigestUtils

/**
 * The traditional bag o' stuff that doesn't quite fit in anywhere else.
 */
object Utils {

  /** Return hash of given string in hex format. */
  def stringHash(str: String) = DigestUtils.md5Hex(str)

  /**
   * @returns a pair of (original extension, target extension).
   * The former represents the original extension of the file.
   * The latter represents the target extension in a request for image conversion.
   *
   * @throws IllegalArgumentException if the given file name has no extension at all.
   */
  def fileExtension(filename: String): (Option[String], Option[String]) = filename.lastIndexOf(".") match {
    case -1 => (None, None)
    case pos => (Some(filename.substring(pos + 1, filename.size).toLowerCase), None)
  }

}