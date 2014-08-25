
package co.ords.w;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Random;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

public class Global {

	public static boolean isValidPicture(String inc_picture)
	{
		if(inc_picture == null || inc_picture.isEmpty())
			return false;
		String hostname = Global.getStandardizedHostnameFromURL(inc_picture);
		if(hostname == null)
			return false;
		if(hostname.equals("graph.facebook.com") && inc_picture.endsWith("picture?width=128&height=128"))
			return true;
		if(hostname.endsWith(".googleusercontent.com") && inc_picture.endsWith("photo.jpg?sz=128"))
			return true;
		if(hostname.equals("www.gravatar.com") && inc_picture.indexOf("www.gravatar.com/avatar/") != -1 &&
				(inc_picture.endsWith("?d=mm&s=128") || inc_picture.endsWith("?d=retro&s=128") || inc_picture.endsWith("?d=monsterid&s=128") || inc_picture.endsWith("?d=identicon&s=128") || inc_picture.endsWith("?d=wavatar&s=128") ))
			return true;
		if(hostname.equals("unicornify.appspot.com") && inc_picture.indexOf("unicornify.appspot.com/avatar/") != -1 && inc_picture.endsWith("?s=128"))
			return true;
		return false;
	}

	public static boolean isWholeNumeric(String incoming_string) // only works for whole numbers, positive and negative
	{
		  int x=0;
		  while(x < incoming_string.length())
		  {
			  if((x==0 && incoming_string.substring(0,1).equals("-")) ||  // OK if first element is "-"
					  incoming_string.substring(x,x+1).equals("0") || incoming_string.substring(x,x+1).equals("1") ||
					  incoming_string.substring(x,x+1).equals("2") || incoming_string.substring(x,x+1).equals("3") ||
					  incoming_string.substring(x,x+1).equals("4") || incoming_string.substring(x,x+1).equals("5") ||
					  incoming_string.substring(x,x+1).equals("6") || incoming_string.substring(x,x+1).equals("7") ||
					  incoming_string.substring(x,x+1).equals("8") || incoming_string.substring(x,x+1).equals("9"))
			  {
				  // ok
			  }
			  else
			  {
				  return false;
			  }
			  x++;
		  }
		  return true;
	}	

	public static String agoIt(Timestamp incoming_timestamp)
	{
	  Calendar now = Calendar.getInstance();
	  Calendar then = Calendar.getInstance();
	  String creationdate = incoming_timestamp.toString();
	  then.set((new Integer(creationdate.substring(0,4))).intValue(),(new Integer(creationdate.substring(5,7))).intValue()-1,(new Integer(creationdate.substring(8,10))).intValue(),(new Integer(creationdate.substring(11,13))).intValue(),(new Integer(creationdate.substring(14,16))).intValue(),(new Integer(creationdate.substring(17,19))).intValue());
	  long millis_ago = (new Long((now.getTimeInMillis() - then.getTimeInMillis()))).longValue();
	  int minutes_ago = (new Long(millis_ago/60000)).intValue();
	  int time_ago = 0;
	  String time_ago_units = "";
	  if(minutes_ago < 60)
	  {
		  time_ago = (new Long((now.getTimeInMillis() - then.getTimeInMillis()))).intValue()/60000;
		  time_ago_units = "mins";
	  }
	  else if ((minutes_ago > 60) && (minutes_ago < 1440))
	  {
		  time_ago = minutes_ago / 60;
		  time_ago_units = "hrs";
	  }
	  else
	  {	
		  time_ago = minutes_ago / 1440;
		  time_ago_units = "days";
	  }
	  if(time_ago == 1)
		  return (time_ago + " " + time_ago_units.substring(0,time_ago_units.length() -1) + " ago");
	  else
		  return (time_ago + " " + time_ago_units + " ago");
	}

	// Acceptable URLs for WORDS should be pages or sites of distinct, public content that appears relatively the same for everyone
	// does it start with http:// or https://? -- maybe allow ftp in the future?
	// does the hostname have a dot? -- if not, it's not a public hostname
	// is it lacking a port number? -- presence of a port number calls into question the public accessibility of the page. When I use a port number, I'm almost always accessing something private.
	public static boolean isValidURLFormation(String inc_url, boolean require_proto) // must have proto or doesn't matter
	{
		//System.out.println("Global.isValidURLFormation(): checking url=" + inc_url);
		if(inc_url == null)
		{
			System.err.println("Global.isValidURLFormation(): not valid url formation, url was null");
			return false;
		}
		URL url = null;
		
		// check for expected presence or non-presence of protocol://
		boolean has_proto = false;
		if(require_proto && !(inc_url.startsWith("http://") || inc_url.startsWith("https://")))
		{
			System.err.println("Global.isValidURLFormation(): not valid url formation, didn't start with http:// or https:// when it was expected");
			has_proto = false; // unnecessary, but meh
			return false;
		}
		else // doesn't require proto, but might have it. Let's see
		{
			if(!(inc_url.startsWith("http://") || inc_url.startsWith("https://")))
				has_proto = false;
			else
				has_proto = true;
		}
		// check for presence of #
		if(inc_url.indexOf("#") != -1)
		{
			System.err.println("Global.isValidURLFormation(): not valid url formation, contained #");
			return false;
		}
		
		// check for at least one slash after protocol:// when expected or at least one slash starting from index 4 (since "t.co/" would be the shortest possible)
		if(has_proto && inc_url.indexOf("/", inc_url.indexOf("://") + 3) == -1)
		{
			System.err.println("Global.isValidURLFormation(): not valid url formation, did not contain \"/\" after \"://\"");
			return false;
		}
		else if(!has_proto && inc_url.indexOf("/", 4) == -1) // we know proto is not required here due to previous check
		{
			System.err.println("Global.isValidURLFormation(): not valid url formation, did not contain any \"/\" after 4th character");
			return false;
		}

		// for the rest of the checks, make sure the url has protocol:// so we can use the URL class to test it
		if(!has_proto) 
			inc_url = "http://" + inc_url; 
		try {
			url = new URL(inc_url);
			String hostname = url.getHost();
			if(hostname.length() < 4)
			{
				System.err.println("Global.isValidURLFormation(): hostname must be at least 4 total chars (i.e. \"t.co\")");
				return false;
			}
			if(hostname.indexOf(".") == -1)
			{
				System.err.println("Global.isValidURLFormation(): not valid url formation, didn't contain \".\"");
				return false;
			}
			if(hostname.indexOf("..") != -1)
			{
				System.err.println("Global.isValidURLFormation(): not valid url formation, contained \"..\"");
				return false;
			}
			
			if(hostname.startsWith("."))
			{
				System.err.println("Global.isValidURLFormation(): not valid url formation, starts with \".\"");
				return false;
			}
			if(hostname.endsWith("."))
			{
				System.err.println("Global.isValidURLFormation(): not valid url formation, ends with \".\"");
				return false;
			}
			if(url.getPort() != -1) // system does not currently deal with urls where a port is specifically set, implied 80 only
			{
				System.err.println("Global.isValidURLFormation(): not valid url formation, contained port specification");
				return false;
			}
		} catch (MalformedURLException e1) {
			System.err.println("Global.isValidURLFormation(): not valid url formation, malformed message="+ e1.getMessage());
			e1.printStackTrace();
			return false; // error encountered, return "null" string;
		}
		return true;
	}

	// Changes example.com with one "." to www.example.com. If there are two dots, it does nothing.
	public static String getStandardizedHostnameFromURL(String inc_url) //always the same
	{
		if(!isValidURLFormation(inc_url, false)) // don't require proto
			return null;
		URL url = null;
		String hostname=null;
		if(!(inc_url.startsWith("http://") || inc_url.startsWith("https://")))
			inc_url = "http://" + inc_url; // give it a dummy protocol
		try {
			url = new URL(inc_url);
			hostname = url.getHost();
			if(!hostname.startsWith("www.") && hostname.indexOf(".", hostname.indexOf(".") + 1) == -1)
			{ // if the hostname doesn't start with www. && it had only one dot (hostname must have at least 2) 
				hostname = "www." + hostname;
			}
		} catch (MalformedURLException e1) {
			// this will NEVER EVER happen because we checked valid URL formation at the beginning of this method
		}
		return hostname;
	}

	// Changes example.com with one "." to www.example.com. If there are two dots, it does nothing.
	public static String getStandardizedHPFromURL(String inc_url) //, boolean shortened)
	{
		if(!isValidURLFormation(inc_url, false)) // don't require proto
		{
			System.err.println("Global.getStandardizedHPFromURL(): not valid url formation");
			return null;
		}
		URL url = null;
		String hostname=null;
		String returnvalue=null;
		if(!(inc_url.startsWith("http://") || inc_url.startsWith("https://")))
			inc_url = "http://" + inc_url; // give it a dummy protocol
		try {
			url = new URL(inc_url);
			hostname = url.getHost();
			if(!hostname.startsWith("www.") && hostname.indexOf(".", hostname.indexOf(".") + 1) == -1)
			{ // if the hostname doesn't start with www. && it had only one dot (hostname must have at least 2) 
				hostname = "www." + hostname;
			}
			returnvalue = hostname + url.getPath();
		} catch (MalformedURLException e1) {
			// this will NEVER EVER happen because we checked valid URL formation at the beginning of this method
		}
		return returnvalue;
	}

	// An HPQSP *ALWAYS* depends on the HP.
	// If the HP doesn't exist, then the HPQSP is simply the generic HP + "?"
	// Requires protocol
	public static String getHPQSPAccordingToThisURL(String inc_url, WordsMapper mapper)
	{
		//System.out.println("Global.getHPQSPAccordingToThisURL(): begin");
		if(!isValidURLFormation(inc_url, true)) // require proto
			return null;
		HPItem hpitem = mapper.load(HPItem.class, getStandardizedHPFromURL(inc_url), new DynamoDBMapperConfig(DynamoDBMapperConfig.ConsistentReads.EVENTUAL));
		if(hpitem == null)
		{
			return getStandardizedHPFromURL(inc_url) + "?";
		}
		else
		{
			return hpitem.getHPQSPAccordingToThisHP(inc_url);
		}
	}
	
	public static void main(String [] args)
	{
		//Global g = new Global();
		//String base62val = Global.fromDecimalToBase62(7, 1356766928000L);
		//System.out.println(base62val);
		//Long decimalval = Global.fromOtherBaseToDecimal(62, base62val);
		//System.out.println(decimalval);


		//System.out.println(g.getChannelInfo());
		/*Calendar cal = Calendar.getInstance();
		long millis = cal.getTimeInMillis();
		//System.out.println(millis);
		String val = "";
		//System.out.println(val = g.fromDecimalToBase62(7, millis));
		//System.out.println(g.fromOtherBaseToDecimal(62, val));*/
	}


}