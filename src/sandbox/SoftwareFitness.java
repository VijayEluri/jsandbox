
package sandbox;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;



public class SoftwareFitness extends AbstractApplication {
	private static final Logger LOG=Logger.getLogger("jsandbox");

	
	private static class StreamBoozer extends Thread
		{
	    private InputStream in;
	    private PrintStream pw;
		private String prefix;
	
		public StreamBoozer(InputStream in, PrintStream pw,String prefix)
			{
	        this.in = in;
	        this.pw = pw;
	        this.prefix=prefix;
			}
	
	    @Override
	    public void run()
	    	{
	    	boolean begin=true;
	    	try {
	    		int c;
	    		while((c=in.read())!=-1)
	    			{
	    			if(begin) pw.print(prefix);
	    			pw.write((char)c);
	    			begin=(c=='\n');
	    			}
	    	 	}
	    	catch(Exception err)
	    		{
	    		err.printStackTrace();
	    		LOG.severe("StreamBoozer error");
	    		}
	    	finally
	    		{
	    		try{in.close();} catch(Exception e2) {}
	    		}
	    	}
		}
	
	private static abstract class VariableDef {
		final String name;
		protected VariableDef(final Element root) {
			final Attr att = root.getAttributeNode("name");
			if(att==null) throw new RuntimeException("@name missing");
			this.name = att.getValue();
			}
		abstract VariableInstance newInstance(final Random rand);
	}
	
	private static abstract class VariableInstance {
		abstract VariableDef getVariableDef();
		abstract Node toNode(Document owner);
		abstract VariableInstance mute(final Random random);
	}
	
	private static class BooleanVariableDef extends VariableDef {
		BooleanVariableDef(final Element root) {
			super(root);
			}
		
		@Override VariableInstance newInstance(final Random rand) {
			BooleanVariable v = new BooleanVariable();
			v.value = rand.nextBoolean();
			v.def = this;
			return v;
		}
		
	}
	
	private static class BooleanVariable extends VariableInstance {
		boolean value;
		BooleanVariableDef def;
		@Override VariableInstance mute(Random random) {
			BooleanVariable v= new BooleanVariable();
			v.def = this.def;
			v.value = random.nextBoolean();
			return v;
			}
		@Override
		VariableDef getVariableDef() {return def;}
		@Override Node toNode(Document owner) {
			final Element E = owner.createElement("boolean");
			E.setAttribute("value", String.valueOf(value));
			return E;
		}
		@Override
		public String toString() { return def.name+"="+this.value; }
	}
	
	
	private static class DoubleVariableDef extends VariableDef {
		double min;
		double max;
		Optional<Double> delta = Optional.empty();
		
		DoubleVariableDef(Element root) {
			super(root);
			Attr att = root.getAttributeNode("min");
			if(att==null) throw new RuntimeException("@min missing");
			min = Double.parseDouble(att.getValue());
			att = root.getAttributeNode("max");
			if(att==null) throw new RuntimeException("@max missing");
			max = Double.parseDouble(att.getValue());
			if(min>=max)  throw new RuntimeException("@min>=@max");
			
			att = root.getAttributeNode("delta");
			if(att!=null)
				{
				delta=Optional.of(Double.parseDouble(att.getValue()));
				}
			}
		
		
		@Override DoubleVariable newInstance(final Random rand) {
			DoubleVariable v = new DoubleVariable();
			v.value = this.min+  rand.nextDouble()*(max-min);
			v.def = this;
			return v;
		}
	}

	private static class DoubleVariable extends VariableInstance {
		double value;
		DoubleVariableDef def;
		@Override VariableDef getVariableDef() { return def;}
		
		@Override Node toNode(Document owner) {
			final Element E = owner.createElement("double");
			E.setAttribute("value", String.valueOf(value));
			return E;
		}
		
		@Override DoubleVariable mute(final Random random) {
			final DoubleVariable v=new DoubleVariable();
			v.def=this.def;
			if(def.delta.isPresent()) {
				boolean done=false;
				while(!done)	{
					done = true;
					v.value += (random.nextDouble()*def.delta.get().doubleValue())*(random.nextBoolean()?-1.0:1.0);
					
					if(v.value<def.min) {
						done=false;
						v.value = def.min;
						}
					else if(v.value>def.max) {
						done=false;
						v.value = def.max;
						}
					}
				}
			else
				{
				v.value = def.min+  random.nextDouble()*(def.max-def.min);
				}
			return v;
			}
		@Override
		public String toString() { return def.name+"="+this.value; }
	}


	
	private static class IntVariableDef extends VariableDef {
		int min;
		int max;
		Optional<Integer> delta = Optional.empty();

		IntVariableDef(final Element root) {
			super(root);
			Attr att = root.getAttributeNode("min");
			if(att==null) throw new RuntimeException("@min missing");
			min = Integer.parseInt(att.getValue());
			att = root.getAttributeNode("max");
			if(att==null) throw new RuntimeException("@max missing");
			max = Integer.parseInt(att.getValue());
			if(min>=max)  throw new RuntimeException("@min>=@max");
			
			att = root.getAttributeNode("delta");
			if(att!=null)
				{
				delta=Optional.of(Integer.parseInt(att.getValue()));
				}
			}
		
		@Override IntVariable newInstance(final Random rand) {
			IntVariable v = new IntVariable();
			v.value = this.min+  rand.nextInt(max-min);
			v.def = this;
			return v;
		}
	}
	
	private static class IntVariable extends VariableInstance {
		int value;
		IntVariableDef def;
		
		@Override Node toNode(Document owner) {
			final Element E = owner.createElement("int");
			E.setAttribute("value", String.valueOf(value));
			return E;
		}
		
		@Override IntVariableDef getVariableDef() {return def;}
		
		@Override IntVariable mute(final Random random) {
			final IntVariable v=new IntVariable();
			v.def=this.def;
			if(def.delta.isPresent()) {
				boolean done=false;
				while(!done)
					{
					done=true;
					v.value += (random.nextInt(def.delta.get().intValue())*(random.nextBoolean()?-1:1));
					if(v.value<def.min) {
						done=false;
						v.value = def.min;
						}
					else if(v.value>=def.max) {
						done=false;
						v.value = def.max-1;
						}
					}
				}
			else
				{
				v.value =  def.min+  random.nextInt(def.max-def.min);
				}
			return v;
			}
		@Override
		public String toString() { return def.name+"="+this.value; }
	}


	private static class EnumVariableDef extends VariableDef {
		final List<String> items = new ArrayList<String>();
		
		EnumVariableDef(final Element root) {
			super(root);
			for(Node c=root.getFirstChild();c!=null;c=c.getNextSibling()) {
				if(c.getNodeType()!=Node.ELEMENT_NODE) continue;
				if(c.getNodeName().equals("item"))
					{
					items.add(c.getTextContent());
					}
				}
			if(this.items.isEmpty()) throw new RuntimeException("no items");
			}
		
		@Override EnumVariable newInstance(final Random rand) {
			EnumVariable v = new EnumVariable();
			v.value = this.items.get(rand.nextInt(items.size()));
			v.def = this;
			return v;
		}

	}

	private static class EnumVariable extends VariableInstance {
		String value;
		EnumVariableDef def;
		@Override VariableDef getVariableDef() { return def; }
		@Override EnumVariable mute(Random random) {
			EnumVariable v = new EnumVariable();
			v.value = this.def.items.get(random.nextInt(def.items.size()));
			v.def = this.def;
			return v;
			}
		@Override Node toNode(Document owner) {
			final Element E = owner.createElement("string");
			E.setAttribute("value", String.valueOf(value));
			return E;
		}
		@Override
		public String toString() { return def.name+"="+this.value; }
	}

	
	private static class Solution {
		static long ID_GENERATOR=0L;
		final long id = ++ID_GENERATOR;
		final List<VariableInstance> variables = new ArrayList<>();
		Optional<Double> fitness = Optional.empty();
		int returnStatus=-1;
		
		@Override
		public String toString() {
			return variables.toString();
			}
	}

	
	private static class SolutionRunner implements Callable<Solution>
		{
		final Solution sol;
		final Element rootXslStylesheet;
		final long timeoutSecs;
		SolutionRunner(final Solution sol,final Element rootXslStylesheet,long timeoutSecs) {
			this.sol=sol;
			this.rootXslStylesheet = rootXslStylesheet;
			this.timeoutSecs = timeoutSecs;
			}
		private VariableInstance findVarInstanceByName(final String refName ) {
		
			for(VariableInstance v: this.sol.variables) {
				if(v.getVariableDef().name.equals(refName)) {
					return v;
				}
			}
			throw new RuntimeException("Cannot find ref="+refName);
			}
		@Override
		public Solution call() throws Exception
			{
			final File shellScriptFile = File.createTempFile("tmp.", ".bash");
			final File outputFile = File.createTempFile("tmp.", ".txt");
			StreamBoozer procStderr =null;
			StreamBoozer procStdout =null;
			
			try
				{
			final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			final DocumentBuilder db=dbf.newDocumentBuilder();
			final Document dom = db.newDocument();
			final Element rootElement =dom.createElement("config");
			dom.appendChild(rootElement);
			rootElement.setAttribute("output", outputFile.getPath());
			rootElement.setAttribute("id", String.valueOf(sol.id));
			final Element varElement =dom.createElement("variables");
			rootElement.appendChild(varElement);
			for(VariableInstance v:this.sol.variables) 
				{
				varElement.appendChild(v.toNode(dom));
				}
			final TransformerFactory trf=TransformerFactory.newInstance();
			final  Templates templates=trf.newTemplates(new DOMSource(this.rootXslStylesheet));
			final  Transformer  tr=templates.newTransformer();
			tr.setOutputProperty(OutputKeys.METHOD,"text");
			tr.transform(
					new DOMSource(dom),
					new StreamResult(shellScriptFile)
					);
			
			//make executable
			Runtime.getRuntime().exec("chmod u+x "+shellScriptFile);
		
			
			List<String> cmdargs = new ArrayList<>();
			
			ProcessBuilder procbuilder= new ProcessBuilder(cmdargs);
			procbuilder.directory(shellScriptFile.getParentFile());
		
			Process proc = procbuilder.start();
			procStderr = new StreamBoozer(proc.getErrorStream(),
					System.err,
					"[make]"
					);
			procStdout = new StreamBoozer(proc.getInputStream(),
					System.out,
					"[make]"
					);
			procStderr.start();
			procStdout.start();
			
			LOG.info("wait for make");
			proc.waitFor(this.timeoutSecs,TimeUnit.SECONDS);
			this.sol.returnStatus = proc.exitValue();
			if(this.sol.returnStatus!=0)
				{
				LOG.severe("solutio, failed");
				}
			else if(outputFile.exists())
				{
				this.sol.fitness = Optional.of( Double.parseDouble( IOUtils.readFileContent(outputFile).trim()));
				}
			else
				{
				LOG.severe("output file was not created");
				}
			
			
			return sol;
			}
		finally
			{
			shellScriptFile.delete();
			outputFile.delete();
			}
		}
		}

	
	private List<VariableDef> variablesDefs = new ArrayList<>();
	private Random random = new Random(System.currentTimeMillis());
	private File tmpDir=null;
	
	private Solution makeSolution() {
		final Solution sol = new Solution();
		for(final VariableDef def: this.variablesDefs) {
			sol.variables.add(def.newInstance(this.random));
		}
		return sol;
	}
	
	
	
	@Override
	protected void fillOptions(final Options options) {
		options.addOption(Option.builder("j").longOpt("jobs").hasArg(true).desc("Number of parallel jobs").build());
		super.fillOptions(options);
		}
	
	
	@Override
	protected int execute(final CommandLine cmd)
	{
		final List<String> args = cmd.getArgList(); 
		if(args.size()!=1) 
			{
			LOG.severe("expected one xml file as input");
			return -1;
			}
		Element xsltStylesheetRoot= null;
		try {
			final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			dbf.setCoalescing(true);
			final DocumentBuilder db=dbf.newDocumentBuilder();
			final Document dom = db.parse(new File(args.get(0)));
			final Element root=dom.getDocumentElement();
			for(Node n1=root.getFirstChild();n1!=null;n1=n1.getNextSibling())
				{
				if(n1.getNodeType()!=Node.ELEMENT_NODE) continue;
				final Element E1= Element.class.cast(n1);
				if(n1.getNodeName().equals("variables")) 
					{
					for(Node n2=n1.getFirstChild();n2!=null;n2=n2.getNextSibling())
						{
						if(n2.getNodeType()!=Node.ELEMENT_NODE) continue;
						final Element E2= Element.class.cast(n2);
						if(n2.getNodeName().equals("double"))
							{
							this.variablesDefs.add(new DoubleVariableDef(E2));
							}
						else if(n2.getNodeName().equals("int") || n2.getNodeName().equals("integer"))
							{
							this.variablesDefs.add(new IntVariableDef(E2));
							}
						else if(n2.getNodeName().equals("bool") || n2.getNodeName().equals("boolean"))
							{
							this.variablesDefs.add(new BooleanVariableDef(E2));
							}
						else if(n2.getNodeName().equals("items"))
							{
							this.variablesDefs.add(new EnumVariableDef(E2));
							}
						else
							{
							System.err.println("unknown node :"+E2.getNodeName());
							return -1;
							}
						}
					}
				else if(E1.getLocalName().equals("stylesheet") &&
						E1.getNamespaceURI().equals("http://www.w3.org/1999/XSL/Transform")
					) 
					{
					xsltStylesheetRoot = Element.class.cast(n1);
					}
				}
			if(xsltStylesheetRoot==null)
				{
				System.err.println("Undefined xsl:stylesheet");
				return -1;
				}
			
			Solution best=null;
			long iteration=0L;
			for(;;) {
				SolutionRunner runner = new SolutionRunner( this.makeSolution(), xsltStylesheetRoot, 60*60*24);
				Solution currSol = runner.call();
				if( currSol==null ||
					currSol.returnStatus!=0 ||
					!currSol.fitness.isPresent() ||
					(best!=null && best.fitness.get().compareTo(currSol.fitness.get())<0))
					{
					continue;
					}
				best = currSol;
				System.out.println(best);
				if(++iteration>1000L) break;
				}
			
			
			return 0;
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
		
	}

	public static void main(String[] args) {
	new SoftwareFitness().instanceMainWithExit(args);
	}

}
