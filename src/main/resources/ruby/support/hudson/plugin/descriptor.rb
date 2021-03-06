
require 'json'

module Hudson
  module Plugin
    class Descriptor < Java::HudsonModel::Descriptor

      def initialize(name, impl, plugin, java_type)
        super(Java::OrgJruby::RubyObject.java_class)
        @name, @impl, @plugin, @java_type = name, impl, plugin, java_type
      end

      def getDisplayName
        @impl.display_name
      end

      def getT()
        @java_type
      end

      def getConfigPage
        "/ruby/plugin/views/#{@name}/config.jelly".tap do |path|
          puts "getConfigPage -> #{path}"
        end
      end

      def getGlobalConfigPage
        "/ruby/plugin/views/#{@name}/global.jelly".tap do |path|
          puts "getGlobalConfigPage -> #{path}"
        end
      end

      def newInstance(request, form)
        properties = JSON.parse(form.toString(2))
        properties.delete("kind")
        properties.delete("stapler-class")
        instance = @plugin.export(@impl.new(properties))
        puts "instance created: #{instance}"
        return instance
      end

    end

  end
end
