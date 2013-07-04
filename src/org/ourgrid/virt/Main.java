package org.ourgrid.virt;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.ourgrid.virt.model.HypervisorType;
import org.ourgrid.virt.model.VirtualMachineConstants;

@SuppressWarnings("static-access")
public class Main {

	public static void main(String[] args) {
		Options options = new Options();
		options.addOption("vm", true, "The name of the virtual machine");
		options.addOption("user", true, "The user of the virtual machine. (For prepareEnvironment)");
		options.addOption("c", true, "The command to be executed. (For exec)");
		options.addOption("source", true, "The source device. (For clone)");
		options.addOption("target", true, "The target device. (For clone)");
		options.addOption("help", false, "Print this message");
		
		options.addOption(OptionBuilder.withArgName("property=value")
                .hasArgs(2)
                .withValueSeparator()
                .withDescription("Use value for given property")
                .create("D"));
		
		options.addOption(OptionBuilder.withArgName("hypervisor")
        		.hasArgs()
        		.withDescription("The hypervisor type")
        		.isRequired(true)
        		.create('h'));
		
		options.addOption(OptionBuilder.withArgName("method")
        		.hasArgs()
        		.withDescription("The method to be executed")
        		.isRequired(true)
        		.create('m'));
		
		HelpFormatter formatter = new HelpFormatter();
		CommandLineParser parser = new GnuParser();
	    
		try {
	        
	    	CommandLine line = parser.parse(options, args);
	        
	        if (line.hasOption("help")) {
	        	formatter.printHelp("ourvirt", options);
			} else {
				process(line);
			}
	        
	    } catch (ParseException exp) {
	        System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
	        formatter.printHelp("ourvirt", options);
	    } catch (Exception e) {
	    	e.printStackTrace();
	    }
		
	}

	private static void process(CommandLine line) throws Exception {
		
		OurVirt ourVirt = new OurVirt();
		String vmName = line.getOptionValue("vm");
		
		if (vmName != null) {
			Map<String, String> properties  = new HashMap<String, String>();
			for (Entry<Object, Object> entry : line.getOptionProperties("D").entrySet()) {
				properties.put(entry.getKey().toString(), entry.getValue().toString());
			}
			ourVirt.register(vmName, properties);
		}
		
		String method = line.getOptionValue("m");
		String hypervisor = line.getOptionValue("h");
		
		HypervisorType hypervisorType = HypervisorType.valueOf(hypervisor.toUpperCase());
		if (hypervisorType == null){
			throw new ParseException("Hypervisor type " + hypervisor + " not supported by OurVirt.");
		}
		
		if (method.equals("create")) {
			checkMachine(vmName);
			ourVirt.create(hypervisorType, vmName);
		} else if (method.equals("destroy")) {
			checkMachine(vmName);
			ourVirt.destroy(hypervisorType, vmName);
		} else if (method.equals("start")) {
			checkMachine(vmName);
			ourVirt.start(hypervisorType, vmName);
		} else if (method.equals("stop")) {
			checkMachine(vmName);
			ourVirt.stop(hypervisorType, vmName);
		} else if (method.equals("prepareEnvironment")) {
			String user = line.getOptionValue("user");
			if ( user == null ){
				throw new ParseException("User name must be specified.");
			}
			Map<String, String> props = new HashMap<String, String>();
			props.put(VirtualMachineConstants.HOST_USER, user);
			ourVirt.prepareEnvironment(hypervisorType, props);
		} else if (method.equals("exec")) {
			String cmd = line.getOptionValue("c");
			if ( cmd == null ){
				throw new ParseException("Commmand line must be specified.");
			}
			ourVirt.exec(hypervisorType, vmName, cmd);
		} else if (method.equals("clone")) {
			String source = line.getOptionValue("source");
			String target = line.getOptionValue("target");
			if ( source == null || target == null) {
				throw new ParseException("Source and target devices must be specified.");
			}
			ourVirt.clone(hypervisorType, source, target);
		} else {
			throw new ParseException("Method " + method + " not supported by OurVirt.");
		}
	}

	private static void checkMachine(String machine) throws ParseException {
		if (machine == null) {
			throw new ParseException("You must specify a machine name");
		}
	}
	
}
