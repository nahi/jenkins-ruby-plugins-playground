package thefrontside.ci;

import hudson.Extension;
import hudson.util.Secret;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.model.*;

import hudson.util.StreamTaskListener;
import org.jruby.RubyArray;
import org.kohsuke.stapler.DataBoundConstructor;

import org.jruby.embed.ScriptingContainer;
import org.jruby.RubyClass;
import org.jruby.RubyObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class EC2CloudDelegate extends Cloud implements RubyDelegate  {
	@SuppressWarnings({"FieldCanBeLocal"})
    private transient ScriptingContainer ruby;
	private transient RubyClass rubyClass;
	private transient RubyObject rubyObject;

    private final String region;
    private final String accessId;
    private final Secret secretKey;
    private final String privateKey;
    public final int instanceCap;
    private final List<SlaveTemplate> templates;


    @DataBoundConstructor
	public EC2CloudDelegate(String region, String accessId, String secretKey, String privateKey, String instanceCapStr, List<SlaveTemplate> templates) {
		super("ec2-"+region);
        this.region = region;
        this.accessId = accessId;
        this.secretKey = Secret.fromString(secretKey.trim());
        this.privateKey = privateKey;
        if(instanceCapStr == null || instanceCapStr.equals(""))
            this.instanceCap = Integer.MAX_VALUE;
        else
            this.instanceCap = Integer.parseInt(instanceCapStr);
        if( templates == null )
            templates = Collections.emptyList();
        this.templates = templates;

		this.readResolve(); // Set parents
    }

    public EC2CloudDelegate(RubyObject obj) {
        super("ec2-" + (String)obj.getInternalVariable("region"));
        // Cache the ruby object
        ruby = PluginImpl.get().getRuby();
        // Verify we have a EC2Cloud RubyObject
        if( obj.getMetaClass() != (RubyClass)ruby.runScriptlet("EC2Cloud")) {
            region = accessId = privateKey = "ERROR";
            secretKey = null;
            instanceCap = 0;
            templates = Collections.emptyList();            
        } else {
            region = (String)obj.getInternalVariable("region");
            accessId = (String)obj.getInternalVariable("access_id");
            secretKey = (Secret)obj.getInternalVariable("secret_key");
            privateKey = (String)obj.getInternalVariable("private_key");
            instanceCap = 0;//(int)obj.getInstanceVariable("instance_cap");
            templates = Collections.emptyList();
        }
    }

    public Object readResolve() {
        // Cache the ruby objects
        ruby = Hudson.getInstance().getPlugin(PluginImpl.class).getRuby();
        rubyClass = (RubyClass)ruby.runScriptlet("EC2Cloud");

        // Convert non-native types
        RubyArray templates = (RubyArray)ruby.runScriptlet("[]");
        for (SlaveTemplate t: this.templates) {
            t.parent = this;
            templates.add(t.getInstancedObject());
        }

        // Create ruby instance & save
        rubyObject = (RubyObject)ruby.callMethod(rubyClass, "new", region, accessId, secretKey, privateKey, instanceCap, templates);
        PluginImpl.addRubyDelegate(this);

        return this;
    }

    public String getAccessId() {
        return accessId;
    }

    public String getSecretKey() {
        return secretKey.getEncryptedValue();
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getInstanceCapStr() {
        if(instanceCap==Integer.MAX_VALUE)
            return "";
        else
            return String.valueOf(instanceCap);
    }

    public List<SlaveTemplate> getTemplates() {
        return Collections.unmodifiableList(templates);
    }

    public SlaveTemplate getTemplate(String label) {
        System.out.println("EC2CloudDelegate.getTemplate");
        Object value = invoke("get_template", label);

        return (SlaveTemplate)PluginImpl.resolveRubyDelegate((RubyObject)value);
        
//        for (SlaveTemplate t : templates)
//            if(t.containsLabel(label))
//                return t;
//        return null;
    }

	public boolean canProvision(Label label) {
		Object value = invoke("can_provision?", label);
        boolean result = (value != null && value != Boolean.FALSE);

        return result;
	}

    public void doProvision(StaplerRequest req, StaplerResponse rsp, @QueryParameter String ami) throws ServletException, IOException {
        Object rubyResults = invoke("do_provision", ami);

        checkPermission(PROVISION);
        if(ami==null) {
            sendError("The 'ami' query parameter is missing",req,rsp);
            return;
        }
        SlaveTemplate t = getTemplate(ami);
        if(t==null) {
            sendError("No such AMI: "+ami,req,rsp);
            return;
        }

        StringWriter sw = new StringWriter();
        StreamTaskListener listener = new StreamTaskListener(sw);
        { // try
            EC2SlaveDelegate node = t.provision(listener);
            Hudson.getInstance().addNode(node);

            rsp.sendRedirect2(req.getContextPath()+"/computer/"+node.getNodeName());
        }
    }

	public Collection<PlannedNode> provision(Label label, int excessWorkload) {
		// Convert to java
        List<PlannedNode> results = new ArrayList<PlannedNode>();
        Object rubyResults = invoke("provision", label, excessWorkload);

        System.out.println("EC2CloudDelegate.provision(" + label + ", " + excessWorkload + ")");
        System.out.println("  --> " + Object.class.toString());

        // @todo complete object conversion
        
        return results;
	}

	private Object invoke(String method, Object... args) {
		return ruby.callMethod(rubyObject, method, args);
	}

    public RubyObject getInstancedObject() {
        return rubyObject;
    }

    public void setInstancedObject(RubyObject obj) {
        rubyObject = obj; 
    }

    public RubyClass getClassObject() {
        return rubyClass;
    }

    public void setClassObject(RubyClass newClass) {
        rubyClass = newClass;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
	public static class DescriptorImpl extends Descriptor<Cloud> {

		private transient ScriptingContainer ruby;
		private transient RubyClass rubyClass;

		public DescriptorImpl() {
            this.ruby = PluginImpl.get().getRuby();
            // This would be generated as <RubyClass>Delegate
			rubyClass = (RubyClass)ruby.runScriptlet("EC2Cloud");
		}

		public String getDisplayName() {
			return ruby.callMethod(rubyClass, "display_name").toString();
		}

//        public FormValidation doTestConnection(
//                @QueryParameter AwsRegion region,
//                @QueryParameter String accessId,
//                @QueryParameter String secretKey,
//                @QueryParameter String privateKey) throws IOException, ServletException {
//            return null;
//        }
	}
}

