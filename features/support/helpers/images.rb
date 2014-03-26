module KnowsHowToAlterImages
  GRAVITIES = {
    "C" => "Center",
    "E" => "East",
    "NE" => "NorthEast",
    "N" => "North",
    "NW" => "NorthWest",
    "SE" => "SouthEast",
    "S" => "South",
    "SW" => "SouthWest",
    "W" => "West",
  }

  def alter_image(path, attributes: {})
    width = attributes.delete('Width')
    height = attributes.delete('Height')
    resize_method = attributes.delete('Resize Method')

    commands = []

    case resize_method
    when nil
      commands << ["-resize #{width}x#{height}"] if width || height
    when "Crop"
      gravity = GRAVITIES[attributes.delete('Gravity') || "C"]
      commands << "-thumbnail #{width}x#{height}^ -extent #{width}x#{height} -gravity #{gravity}"
    end

    commands << "-format #{attributes.delete('Format')}" if attributes.has_key?('Format')

    raise "I haven't dealt with all the parameters (#{attributes.keys.join(", ")})" unless attributes.empty?

    # Resize the source file to the desired size
    cmd = (%w{mogrify} + commands + [path]).join(" ")
    `#{cmd}`
  end

  def parse_format_details(attributes)
    params = {}
    attributes.each do |k,v|
      params[k.gsub(/^Image: (.).*$/, "img:\\1").downcase] = v.downcase
    end
    params
  end

  def compare_response_image(alter_source: {}, ensure_response_is_compressed: false)
    pending "It looks as though my hamming checker may not be doing what I'm expecting!"
    begin
      path_given = subject(:image)['local_path'] rescue nil
      source_file = Tempfile.new('source-image')

      if path_given
        source_file.close
        FileUtils.cp(File.join(__dir__,"../../support/data",path_given), source_file.path)
      else
        source_file.write subject(:image_data)
        source_file.close
      end

      alter_image(source_file.path, attributes: alter_source)

      received_file = Tempfile.new('received-image')
      received_file.write HttpCapture::RESPONSES.last.body
      received_file.close

      source = Phashion::Image.new(source_file.path)
      received = Phashion::Image.new(received_file.path)

      begin
        expect(received).to be_duplicate(source), "The received image is not similar enough to the source at the requested dimensions"
        expect(File.size(received_file.path)).to be < File.size(source_file.path) if ensure_response_is_compressed
      rescue
        random = (0...8).map { (65 + rand(26)).chr }.join
        FileUtils.cp(source_file.path, "/tmp/#{random}-source.bin")
        FileUtils.cp(received_file.path, "/tmp/#{random}-received.bin")
        puts "Please check /tmp/#{random}-*.bin"
        raise
      end
    ensure
      source_file.unlink if source_file
      received_file.unlink if received_file
    end
  end
end
World(KnowsHowToAlterImages)