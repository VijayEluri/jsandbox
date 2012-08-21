package sandbox;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


// http://www.flickr.com/services/api/flickr.photos.search.html
/* 
<?xml version="1.0" encoding="utf-8" ?>
<rsp stat="ok">
    <photo id="7154235545" owner="36500748@N04" secret="cb0ee4c0ed" server="7085" farm="8" title="_descabelada" ispublic="1" isfriend="0" isfamily="0" license="0" tags="pictures city red brazil urban music woman girl brasil canon photo model girlfriend exposure foto photographer sãopaulo mulher modelo vermelho namorada sp ibirapuera garota urbano música rockandroll exposição oca metrópole canonef50mmf14usm parquedoibirapuera cenaurbana letsrock canonef50mm cidadesbrasileiras clicksp cityofsaopaulo yourcountry abnermerchan lineimarani" ownername=".merchan" url_q="http://farm8.staticflickr.com/7085/7154235545_cb0ee4c0ed_q.jpg" height_q="150" width_q="150">
      <description>Exposição Let's Rock
Oca - Parque Ibirapuera
São Paulo - SP

&lt;b&gt;&amp;gt;&amp;gt;&lt;/b&gt; &lt;a href=&quot;http://www.flickr.com/photos/abnermerchan/&quot; target=&quot;blank&quot;&gt;Flickr&lt;/a&gt; | &lt;a href=&quot;http://www.gettyimages.com/Search/Search.aspx?assettype=image&amp;amp;artist=Abner+Merchan&quot; target=&quot;blank&quot; rel=&quot;nofollow&quot;&gt;Getty Images&lt;/a&gt; | &lt;a href=&quot;http://500px.com/abnermerchan&quot; target=&quot;blank&quot; rel=&quot;nofollow&quot;&gt;500px&lt;/a&gt; | &lt;a href=&quot;http://twitter.com/abnermerchan&quot; target=&quot;blank&quot; rel=&quot;nofollow&quot;&gt;Twitter&lt;/a&gt; | &lt;a href=&quot;http://www.facebook.com/abnermerchan&quot; target=&quot;blank&quot; rel=&quot;nofollow&quot;&gt;Facebook&lt;/a&gt; | &lt;a href=&quot;http://pinterest.com/abnermerchan/&quot; target=&quot;blank&quot; rel=&quot;nofollow&quot;&gt;Pinterest&lt;/a&gt;

&lt;i&gt;Copyright © 2012, Abner Merchan - Todos os direitos reservados.&lt;/i&gt;</description>
    </photo>
 
 * 
 */
public class FlickrRss
	{
	private static Logger LOG=Logger.getLogger("flickr.search");
	private static final String BASE_REST="http://api.flickr.com/services/rest/";
	private enum Format { html,atom};
	private Format format=Format.html;
	private boolean printMime=false;
	private TreeSet<Photo> photos=new TreeSet<FlickrRss.Photo>();
	private int max_count=-1;
	private int start_index=0;
	private ScriptEngine jsEngine;
	private String dateStart=null;
	private String dateEnd=null;
	private boolean enableGroup=true;
	
	static public class Photo implements Comparable<Photo>
		{
		Long id=null;
		String owner=null;
		String secret=null;
		String title=null;
		String license=null;
		String description=null;
		String server;
		String farm;
		String tags;
		Long dateupload;
		String date_taken="";//e.g: 2012-06-26 19:06:42
		int o_width;
		int o_height;
		public String[] getTags()
			{
			return this.tags.split("[ \t]+");
			}
		public String getOwner()
			{
			return this.owner;
			}
		public int getWidth()
			{
			if(this.o_width>this.o_height)
				{
				return 240;
				}
			else
				{
				return (int)((240.0/o_height)*o_width);
				}
			}
		public int getHeight()
			{
			if(this.o_width>this.o_height)
				{
				return (int)((240.0/o_width)*o_height);
				}
			else
				{
				return 240;
				}
			}
		public String getTitle()
			{
			return title.toLowerCase();
			}
		
		public String getLicense()
			{
			return license;
			}
		
		public String getPageURL()
			{
			return "http://www.flickr.com/photos/"+owner+"/"+id+"/";
			}
		
		public String getPhotoURL()
			{
			return "http://farm"+farm+".staticflickr.com/"+server+"/"+id+"_"+secret+"_"+"m"+".jpg";
			}
		
		@Override
		public int hashCode() {
			return id.hashCode();
			}
		
		@Override
		public int compareTo(Photo o)
			{
			if(this==o) return 0;
			Photo other=Photo.class.cast(o);
			if(other.id.equals(this.id)) return 0;
			return other.dateupload.compareTo(this.dateupload);
			}
		
		@Override
		public boolean equals(Object obj)
			{
			return Photo.class.cast(obj).id.equals(this.id);
			}
		
		void writeAtom(XMLStreamWriter w) throws XMLStreamException
			{
			w.writeStartElement("entry");
			writeSimple(w,"title",this.title);
			w.writeEmptyElement("link");
			w.writeAttribute("href", this.getPageURL());
			w.writeStartElement("author");
			writeSimple(w,"name",this.owner);
			w.writeEndElement();
			w.writeEndElement();

			}
		void writeHtml(XMLStreamWriter w) throws XMLStreamException
			{
			w.writeStartElement("div");
			
			w.writeStartElement("a");
			w.writeAttribute("href", this.getPageURL());
			w.writeEmptyElement("img");
			w.writeAttribute("src", this.getPhotoURL());
			if(getWidth()>0 && getHeight()>0)
				{
				w.writeAttribute("width",String.valueOf(this.getWidth()));
				w.writeAttribute("height",String.valueOf(this.getHeight()));
				}
			else
				{
				w.writeAttribute("height","240");
				}
			w.writeEndElement();//a
			
			w.writeEmptyElement("br");
			w.writeCharacters(this.title);
			w.writeEndElement();
			}
		}
	
	private FlickrRss() throws Exception
		{
		ScriptEngineManager mgr = new ScriptEngineManager();
		this.jsEngine = mgr.getEngineByName("JavaScript");
		}
	
	
	protected Photo parsePhoto(StartElement start,XMLEventReader r) throws IOException,XMLStreamException
		{
		Photo p=new Photo();
		Attribute att=start.getAttributeByName(new QName("id"));
		if(att!=null) p.id=new Long(att.getValue());
		att=start.getAttributeByName(new QName("secret"));
		if(att!=null) p.secret=att.getValue();
		att=start.getAttributeByName(new QName("owner"));
		if(att!=null) p.owner=att.getValue();
		att=start.getAttributeByName(new QName("title"));
		if(att!=null) p.title=att.getValue();
		att=start.getAttributeByName(new QName("license"));
		if(att!=null) p.license=att.getValue();
		att=start.getAttributeByName(new QName("server"));
		if(att!=null) p.server=att.getValue();
		att=start.getAttributeByName(new QName("farm"));
		if(att!=null) p.farm=att.getValue();
		att=start.getAttributeByName(new QName("license"));
		if(att!=null) p.license=att.getValue();
		att=start.getAttributeByName(new QName("tags"));
		if(att!=null) p.tags=att.getValue();
		att=start.getAttributeByName(new QName("dateupload"));
		if(att!=null) p.dateupload= new Long(att.getValue());
		att=start.getAttributeByName(new QName("datetaken"));
		if(att!=null) p.date_taken= att.getValue();
		att=start.getAttributeByName(new QName("o_width"));
		if(att!=null) p.o_width= Integer.parseInt(att.getValue());
		att=start.getAttributeByName(new QName("o_height"));
		if(att!=null) p.o_height= Integer.parseInt(att.getValue());
		
		while(r.hasNext())
			{
			XMLEvent evt=r.nextEvent();
			if(evt.isStartElement())
				{
				StartElement e=evt.asStartElement();
				String name=e.getName().getLocalPart();
				if(name.equals("description"))
					{
					p.description=r.getElementText();
					}
				}
			else if(evt.isEndElement())
				{
				EndElement e=evt.asEndElement();
				String name=e.getName().getLocalPart();
				if(name.equals("photo")) break;
				}
			}
		return p;
		}
	
	
	protected List<Photo> parseUrl(String url)throws IOException,XMLStreamException
		{
		LOG.info(url);
		List<Photo> L=new ArrayList<FlickrRss.Photo>();
		InputStream  in=new URL(url).openStream();
		XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
		xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
		XMLEventReader r= xmlInputFactory.createXMLEventReader(in);	
	
		while(r.hasNext())
			{
			XMLEvent evt=r.nextEvent();
			if(evt.isStartElement())
				{
				StartElement e=evt.asStartElement();
				String name=e.getName().getLocalPart();
				if(name.equals("photo"))
					{
					Photo photo=parsePhoto(e, r);
					L.add(photo);
					}
				else if(name.equals("err"))
					{
					System.err.println("ERROR:"+r.getElementText());
					System.exit(-1);
					}

				}
			}
		r.close();
		in.close();
		return L;
		}
	
	private static void writeSimple(XMLStreamWriter w,String tag,String content) throws XMLStreamException
		{
		w.writeStartElement(tag);
		w.writeCharacters(content);
		w.writeEndElement();
		}
	
	private void dump() throws XMLStreamException,IOException
		{
		
		XMLOutputFactory xmlfactory= XMLOutputFactory.newInstance();
		XMLStreamWriter w= xmlfactory.createXMLStreamWriter(System.out,"UTF-8");
		
		if(printMime)
			{
			switch(this.format)
				{
				case atom:System.out.print("Content-type: application/atom+xml\n\n");break;
				default:System.out.print("Content-type: text/html\n\n");break;
				}
			}
		switch(this.format)
			{
			case atom:
				{
				w.writeStartDocument("UTF-8","1.0");
				w.writeStartElement("feed");
				w.writeAttribute("xmlns","http://www.w3.org/2005/Atom");
				writeSimple(w,"title","flickr");
				writeSimple(w,"updated",new SimpleDateFormat("yyyy-MM-dd'T'h:m:ssZ").format(new Date()));
				writeSimple(w,"subtitle","flickr");
				writeSimple(w,"generator",getClass().getName());
				
				for(Photo photo: this.photos)
					{
					photo.writeAtom(w);
					}
				w.writeEndElement();
				w.writeEndDocument();
				break;
				}
			case html:
				{
				final int num_cols=4;
				int x=0;
				w.writeStartElement("html");
				w.writeStartElement("body");
				w.writeStartElement("table");
				for(Photo photo: this.photos)
					{
					if(x==0) w.writeStartElement("tr");
					w.writeStartElement("td");
					photo.writeHtml(w);
					w.writeEndElement();
					++x;
					if(x==num_cols)
						{
						w.writeEndElement();
						x=0;
						}
					}
				if(x!=0) w.writeEndElement();//tr
				w.writeEndElement();//table
				w.writeEndElement();
				w.writeEndElement();
				break;
				}
			}
				
		w.flush();
		w.close();
		System.out.println();
		}
	
	private Map<String,String> getArguments(
			Node root,
			Map<String,String> args)
		{
		args.put("extras","o_dims,license,description,owner_name,icon_server,tags,date_upload");
		if(root==null) return args;
		for(Node c2=root.getFirstChild();c2!=null;c2=c2.getNextSibling())
			{
			if(c2.getNodeType()!=Node.ELEMENT_NODE) continue;
			if("arg".equals(c2.getNodeName()))
					{
					String attName=Element.class.cast(c2).getAttribute("name");
					if(attName.isEmpty() || args.containsKey(attName)) continue;
					String attValue=c2.getTextContent();
					args.put(attName, attValue);
					}
			}
		Node parent=root.getParentNode();
		return getArguments(parent,args);
		}
	
	private String getScript(Node root,String js)
		{
		if(root==null) return js;
		for(Node c2=root.getFirstChild();c2!=null;c2=c2.getNextSibling())
			{
			if(c2.getNodeType()!=Node.ELEMENT_NODE) continue;
			if(!("script".equals(c2.getNodeName()))) continue;
			js=c2.getTextContent()+"\n"+js;
			}
		Node parent=root.getParentNode();
		return getScript(parent,js);
		}
	
	private void recursive(Node root)throws IOException,XMLStreamException
		{
		for(Node c1=root.getFirstChild();
			c1!=null;
			c1=c1.getNextSibling())
			{
			if("div".equals(c1.getNodeName()))
				{
				recursive(c1);
				continue;
				}
			else if ( "query".equals(c1.getNodeName()))
				{
				Map<String,String> args=getArguments(c1, new HashMap<String,String>());
				String script=getScript(c1,"");
				//System.err.println("script:"+script);
				StringBuilder url=new StringBuilder(BASE_REST);
				for(String attName:args.keySet())
					{
					String attValue=args.get(attName);
					url.append(url.length()==BASE_REST.length()?'?':'&');
					url.append(attName);
					url.append('=');
					url.append(URLEncoder.encode(attValue, "UTF-8"));
					}
				if(dateStart!=null)
					{
					url.append("&min_upload_date=");
					url.append(URLEncoder.encode(dateStart, "UTF-8"));
					}
				if(dateEnd!=null)
					{
					url.append("&max_upload_date=");
					url.append(URLEncoder.encode(dateEnd, "UTF-8"));
					}
			
				SimpleBindings bind=new SimpleBindings();
				
				List<Photo> L=new ArrayList<Photo>();
				if(enableGroup || "flickr.groups.pools.getPhotos".equals(args.get("method"))==false )
					{
					L=parseUrl(url.toString());
					}
				if(!script.isEmpty())
					{

					for	(Photo p:L)
						{
						bind.put("photo", p);
						try
							{
							if(jsEngine.eval(script,bind).equals(Boolean.TRUE))
								{
								this.photos.add(p);
								}
							}
						catch (ScriptException e)
							{
							e.printStackTrace();
							}
						}
					}
				else
					{
					this.photos.addAll(L);
					}
				if(this.max_count!=-1)
					{
					while(!this.photos.isEmpty() &&
						  this.photos.size()> (this.start_index+this.max_count) )
						{
						Photo last=this.photos.last();
						this.photos.remove(last);
						}
					}
				}
			}	
		}

	
	private void run(File config) throws IOException,XMLStreamException
		{
	
		try {
			
			
			DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			DocumentBuilder builder=factory.newDocumentBuilder();
			Document dom=builder.parse(config);
			Element root=dom.getDocumentElement();
			recursive(root);
			//remove head
			for(int i=0;i<this.start_index && !this.photos.isEmpty();++i)
				{
				Iterator<Photo> iter=this.photos.iterator();
				iter.next();
				iter.remove();
				}
			
			dump();
			System.exit(0);
			}
		catch (Exception e) 
			{
			e.printStackTrace();
			}
		}
	
	public static void main(String[] args) throws Exception
		{
		FlickrRss app=new FlickrRss();
		int optind=0;
		while(optind< args.length)
			{
			if(args[optind].equals("-h") ||
			   args[optind].equals("-help") ||
			   args[optind].equals("--help"))
				{
				System.err.println("Options:");
				System.err.println(" -h help; This screen.");
				System.err.println(" -proxyHost <host>.");
				System.err.println(" -proxyPort <port>.");
				System.err.println(" -n <count>.");
				System.err.println(" -s <start0>.");
				System.err.println(" --date-start <YYYYMMDD>.");
				System.err.println(" --date-end <YYYYMMDD>.");
				System.err.println(" --html --atom ");
				System.err.println(" --mime ");
				System.err.println(" --no-group ");
				return;
				}
			else if(args[optind].equals("--no-group"))
				{
				app.enableGroup=false;
				}
			else if(args[optind].equals("--date-start"))
				{
				app.dateStart=args[++optind];
				}
			else if(args[optind].equals("--date-end"))
				{
				app.dateEnd=args[++optind];
				}
			else if(args[optind].equals("--date"))
				{
				app.dateStart=args[++optind];
				app.dateEnd=app.dateStart+"  23:59:59";
				app.dateStart+="  00:00:01";
				}
			else if(args[optind].equals("-proxyHost"))
				{
				System.setProperty("http.proxyHost", args[++optind]);
				}
			else if(args[optind].equals("-proxyPort"))
				{
				System.setProperty("http.proxyPort", args[++optind]);
				}
			else if(args[optind].equals("-n") && optind+1< args.length)
				{
				app.max_count=Integer.parseInt(args[++optind]);
				}
			else if(args[optind].equals("-s") && optind+1< args.length)
				{
				app.start_index=Integer.parseInt(args[++optind]);
				}	
				
			else if(args[optind].equals("--atom"))
				{
				app.format=Format.atom;
				}
			else if(args[optind].equals("--html"))
				{
				app.format=Format.html;
				}
			else if(args[optind].equals("--mime"))
				{
				app.printMime=true;
				}
			else if(args[optind].equals("--"))
				{
				optind++;
				break;
				}
			else if(args[optind].startsWith("-"))
				{
				System.err.println("Unknown option "+args[optind]);
				return;
				}
			else 
				{
				break;
				}
			++optind;
			}
		
		if(optind+1!=args.length)
			{
			System.err.println("Illegal number of arguments.");
			return;
			}
		String filename=args[optind++];
		app.run(new File(filename));
		}
}
