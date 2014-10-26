
package co.ords.w;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.StringTokenizer;
import java.util.TreeMap;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName="words_hps")
public class HPItem implements java.lang.Comparable<HPItem> {

	// static parts of the database entry
	private String hostname;
	private String hp;
	private Set<String> significant_qsps; // on youtube, watch/v is significant because it varies the display. watch/feature and watch/list, etc are not because they don't
	private String original_url; // always standardized

	// dynamic parts (not in the database entry itself)
	//private TreeSet<HPQSPItem> hpqsps; // this is ALL of the hpqsps which can be gotten with a query against the secondary index. Not sure it's needed, though.

	@DynamoDBHashKey(attributeName="hp") 
	public String getHP() {return hp; }
	public void setHP(String hp) { this.hp = hp; }

	@DynamoDBIndexHashKey(globalSecondaryIndexName="hostname-index", attributeName="hostname")
	public String getHostname() {return hostname; }
	public void setHostname(String hostname) { this.hostname = hostname; }

	@DynamoDBAttribute(attributeName="original_url")  
	public String getOriginalURL() {return original_url; }
	public void setOriginalURL(String original_url) { this.original_url = original_url; }

	@DynamoDBAttribute(attributeName="significant_qsps")  
	public Set<String> getSignificantQSPs() {return significant_qsps; }
	public void setSignificantQSPs(Set<String> significant_qsps) { this.significant_qsps = significant_qsps; }

	// do these return alphabetized?
	@DynamoDBIgnore
	public String getHPQSPStringAccordingToThisHP(String inc_url) // should be called getHPQSPAccordingToThisHP(url)?
	{
		//System.out.println("HPItem.getHPQSPAccordingToThisHP(url): entering");
		String hpqsp_string_according_to_this_hp = null;
		if(getSignificantQSPs() == null) 
		{
			return getHP() + "?"; // no significant qsps, then the reduced hpqsp is just the hp + ?
		}
		else
		{
			//System.out.println("HPItem.getHPQSPAccordingToThisHP(url): has significant qsps");
			URL url_object = null;
			try
			{
				if(!(inc_url.startsWith("http://") || inc_url.startsWith("https://")))
					inc_url = "http://" + inc_url; // give it a dummy protocol
				url_object = new URL(inc_url);
				String qsp = url_object.getQuery();
				if(qsp == null || qsp.equals(""))
				{
					String fake_qsp_string = "";
					Set<String> nonordered_sqsps = getSignificantQSPs();
					TreeSet<String> ordered_sqsps = new TreeSet<String>();
					ordered_sqsps.addAll(nonordered_sqsps); // alphabetize them
					Iterator<String> sqsps_it = ordered_sqsps.iterator();
					while(sqsps_it.hasNext())
					{
						fake_qsp_string = fake_qsp_string + sqsps_it.next() + "=" + "&";
					}
					if(fake_qsp_string.endsWith("&"))
						fake_qsp_string = fake_qsp_string.substring(0, fake_qsp_string.length()-1);
					return Global.getStandardizedHPFromURL(inc_url) + "?" + fake_qsp_string;
				}
				else
				{
					//System.out.println("HPItem.getHPQSPAccordingToThisHP(url): url has qsps");
				}
				StringTokenizer st = new StringTokenizer(qsp, "&");
				String currentstring = ""; String left = ""; String right="";
				TreeMap<String, String> tmap = new TreeMap<String, String>();
				while(st.hasMoreTokens())
				{
					currentstring = st.nextToken();
					//System.out.println("HPItem.getHPQSPAccordingToThisHP(url): currentstring=" + currentstring);
					if(currentstring.indexOf("=") == -1) // invalid qsp
					{
						// do nothing
					}
					else
					{
						left = currentstring.substring(0,currentstring.indexOf("="));
						right = currentstring.substring(currentstring.indexOf("=")+1);
						//System.out.println("HPItem.getHPQSPAccordingToThisHP(url): adding left=" + left + " right=" + right);
						tmap.put(left,right);
					}
				}
				String currentkey = "";
				String newqsp = "";
				//System.out.println("HPItem.getHPQSPAccordingToThisHP(url): tmap.size()=" + tmap.size());
				while(tmap.size() > 0)
				{
					currentkey = tmap.firstKey();
					//System.out.println("HPItem.getHPQSPAccordingToThisHP(url): currentkey=" + currentkey);
					if(getSignificantQSPs().contains(currentkey))
					{
						//System.out.println("HPItem.getHPQSPAccordingToThisHP(url):" + currentkey + "=" + tmap.get(currentkey));
						newqsp = newqsp + currentkey + "=" + tmap.get(currentkey) + "&";
					}
					tmap.remove(currentkey);
				}
				if(newqsp.endsWith("&"))
					newqsp = newqsp.substring(0, newqsp.length()-1);
				//System.out.println("HPItem.getHPQSPAccordingToThisHP(url): original: " + hp + "?" + qsp);
				//System.out.println("HPItem.getHPQSPAccordingToThisHP(url): reduced : " + hp + "?" + newqsp);
				if(newqsp != null && !newqsp.equals(""))
					hpqsp_string_according_to_this_hp = hp + "?" + newqsp;
				else
					hpqsp_string_according_to_this_hp = hp + "?";
			}
			catch(MalformedURLException murle)
			{
				hpqsp_string_according_to_this_hp = null;
				System.err.println("HP.getHPQSPAccordingToThisHP(url): " + murle.getMessage());
			}
		}
		//System.out.println("HPItem.getHPQSPAccordingToThisHP(url): exiting with hpqsp_string_according_to_this_hp=" + hpqsp_string_according_to_this_hp);
		return hpqsp_string_according_to_this_hp;
	}

	@DynamoDBIgnore
	public int compareTo(HPItem o) // this makes more recent comments come first
	{
	    String otherhp = ((HPItem)o).getHP();
	    int x = otherhp.compareTo(getHP());
	    if(x >= 0) // this is to prevent equals
	    	return 1;
	    else
	    	return -1;
	}
}