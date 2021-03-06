
module Hudson
  module Plugin

    class Controller
      attr_reader :descriptors

      def initialize(java)
        @java = java
        @start = @stop = proc {}
        @descriptors = {}
        @wrappers = {}
        require 'bundled-gems.jar'
        require 'rubygems'
        script = 'support/hudson/plugin/models.rb'
        self.instance_eval @java.read(script), script
        DSL.new(self) do |dsl|
          script = @java.read("plugin.rb")
          dsl.instance_eval(script, "plugin.rb")
        end
      end

      def start
        @start.call()
      end

      def stop
        @stop.call()
      end

      def import(object)
        object.respond_to?(:unwrap) ? object.unwrap : object
      end

      def export(object)
        puts "export(#{object})"
        return @wrappers[object] if @wrappers[object]
        case object
          when Hudson::Plugin::Cloud
            puts "it's a cloud, I'm going to wrap it"
            wrapper = Hudson::Plugin::Cloud::Wrapper.new(self, object)
            puts "wrapper created: #{wrapper}"
            return wrapper
          when Hudson::Plugin::BuildWrapper
            puts "it's a build wrapper, I'm going to wrap it"
            wrapper = Hudson::Plugin::BuildWrapper::Wrapper.new(self, object)
          else object
        end
      end

      private

      class DSL
        def initialize(controller)
          @controller = controller
          yield self if block_given?
        end

        def start(&impl)
          @controller.instance_variable_set(:@start, impl) if impl
        end

        def stop(&impl)
          @controller.instance_variable_set(:@stop, impl) if impl
        end
      end

    end
  end
end