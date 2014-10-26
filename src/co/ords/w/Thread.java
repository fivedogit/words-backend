
package co.ords.w;

import java.io.IOException;
import java.io.StringReader;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Iterator;

import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

// creates User object via incoming uid

public class Thread {

	/*
	 * Database: Hostnames have many pages. Pages have many URLs. Thread = all the comments attached to 1 page (separated) or 
	 *  	all the comments on all pages under a hostname (combined). 
	 * This "Thread" class (with no database table strictly associated) is a representation of the comment thread for 
	 * a given page, whether it is "separated" and constructed from one Page or combined and constructed from many Pages.
	 * So, Thread(url) takes a url, checks with hostnames table to determine separated/combined,
	 * then assembles comments, top_level comments, second_level comments, etc. 
	 * Thread contains one or more "Pages" which contain their own "comments" collections. This class' purpose
	 * is to provide methods that make creating, retrieving and parsing these comments easier. 
	 */

	private TreeSet<HPQSPItem> hpqsps;
	private TreeSet<CommentItem> toplevelcomments;
	private TreeSet<CommentItem> allcomments;
	private String original_url; 
	private HostnameItem hostnameitem;
	private TreeSet<HPItem> hps;
	private boolean valid;
	private WordsMapper mapper;
	private DynamoDBMapperConfig dynamo_config;
	private long numlikes = 0; // hostname or hpqsp likes
	
	// NOTE: assumes a valid url and valid channel
	public Thread(String inc_url, WordsMapper inc_mapper, DynamoDBMapperConfig inc_dynamo_config)
	{
		mapper = inc_mapper;
		dynamo_config = inc_dynamo_config;
		valid = false;
		if(!Global.isValidURLFormation(inc_url,true)) // checks http/s://, lack of ":", presence of "." and MalformedURLException // require proto_colon_slashslash
			return; // with valid = false;
		
		// inputs seem to check out, valid= true and continue
		valid = true;

		/* first, look up hostname
		 * if(!hostname exists)
		 * {
		 * 		return null; // no thread for a hostname that doesn't exist
		 * }
		 * else // hostname exists
		 * {
		 * 	 	if(hostname combined)
		 * 		{
		 * 			get all hps
		 * 		  	for(hps)
		 * 			{
		 * 				get all hpqsps
		 * 				for(hpqsps)
		 * 				{
		 * 					get all comments
		 * 					for(comments)
		 * 					{
		 * 						add to the treesets here.
		 * 					}
		 * 				}
		 * 			}
		 * 		}
		 * }
		 */
		toplevelcomments = null; // these start out null. If they've been "populated" (method below) and have nothing, they are empty treesets afterwards
		allcomments = null;
		original_url = inc_url;
		hostnameitem = mapper.load(HostnameItem.class, Global.getStandardizedHostnameFromURL(inc_url), dynamo_config);
		if(hostnameitem == null)
		{
			//System.out.println("Thread(): couldn't find hostname for url: "+inc_url+" standardized:" + Global.getStandardizedHostnameFromURL(inc_url));
			hpqsps = null;
			numlikes = 0L;
			return;
		}
		else 
		{
			/* Here, we are looking for all the "HPQSPS" that make up the "Thread". 
			 * For separated, it's just one HPQSP
			 * For combined, it's all the HPQSPS underneath a Hostname
			 */
			//System.out.println("Thread(): Found hostname: " + Global.getStandardizedHostnameFromURL(inc_url));
			if(hostnameitem.getSeparated())
			{
				// if the hostname is separated, then there is only one HPItem and one HPQSP for this URL.
				// Two possibilities:
				// 1. The HPItem has no significant QSPs. Then just get the "www.example.com/page.html?" HPQSP
				// 2. The HPItem has >0 significant QSPs. Then use the url+HPItem to figure out all the HPQSPs to get "www.example.com/page.html?article=blah"
				// Note: it's possible for a separated hostname to exist with no hps or hpqsps below it.
				HPItem onlyhpitem = mapper.load(HPItem.class, Global.getStandardizedHPFromURL(inc_url), dynamo_config);
				if(onlyhpitem == null)
				{
					// this hostname item has no hps. This is fine. 
					numlikes = 0L;
				}
				else if(onlyhpitem != null) 
				{
					//System.out.println("Thread(): onlyhp=" + onlyhpitem.getHP());
					hps = new TreeSet<HPItem>();
					hps.add(onlyhpitem);
					String hpqsp_string = onlyhpitem.getHPQSPStringAccordingToThisHP(inc_url);
					HPQSPItem onlyhpqspitem = mapper.load(HPQSPItem.class, hpqsp_string, dynamo_config);
					if(onlyhpqspitem != null)
					{
						numlikes = onlyhpqspitem.getNumberOfHPQSPLikes(0, inc_mapper, inc_dynamo_config);
						hpqsps = new TreeSet<HPQSPItem>();
						hpqsps.add(onlyhpqspitem); // onlyhpitem.getRelevantHPQSPs(inc_url, mapper, dynamo_config);
					}
					else
						numlikes = 0L;
				}
				
				// is this right? Why are we looping? Shouldn't it just be one hpqsp?
				// A: Yes, it's just one item, but since the "combined" section below could have multiples, this class has a set of hpqsps as a private variable. 
				// A (cont): Hence, the appearance, here, of multiples when there can be only one at this point.
				if(hpqsps != null) 
				{
					//System.out.println("Thread(): hpqsps was not null. Does it have comments?");
					HPQSPItem currenthpqsp = hpqsps.first();
					/*Iterator<HPQSPItem> hpqsps_it = hpqsps.iterator();
					HPQSPItem currenthpqsp = null;
					toplevelcomments = new TreeSet<CommentItem>();
					allcomments = new TreeSet<CommentItem>();
					while(hpqsps_it.hasNext())
					{
						currenthpqsp = hpqsps_it.next();*/
						if(currenthpqsp != null)
						{
							toplevelcomments = new TreeSet<CommentItem>();
							allcomments = new TreeSet<CommentItem>();
							TreeSet<CommentItem> hpqsp_toplevelcomments = currenthpqsp.getTopLevelComments(525949, mapper, dynamo_config); // one year in minutes
							if(hpqsp_toplevelcomments != null)
								toplevelcomments.addAll(hpqsp_toplevelcomments);
							TreeSet<CommentItem> hpqsp_allcomments = currenthpqsp.getAllComments(525949, mapper, dynamo_config); // one year in minutes
							if(hpqsp_allcomments != null)
								allcomments.addAll(hpqsp_allcomments);
						}
					//}
				}
				
			}
			else // combined
			{
				//System.out.println("Thread(): hostname=combined");
				hps = hostnameitem.getHPs(mapper, dynamo_config);
				hpqsps = hostnameitem.getHPQSPs(mapper, dynamo_config);
				allcomments = hostnameitem.getAllComments(525949, mapper, dynamo_config); // one year in minutes
				toplevelcomments = null;
				numlikes = hostnameitem.getNumberOfHostnameLikes(hostnameitem.getHostname(), 0, mapper, dynamo_config);
				// loop through all comments and build toplevelcomments treeset
				if(allcomments != null)
				{	
					toplevelcomments = new TreeSet<CommentItem>();
					CommentItem ci = null;
					Iterator<CommentItem> c_it = allcomments.iterator();
					while(c_it.hasNext())
					{
						ci = c_it.next();
						if(ci.getParent().indexOf(".") != -1)
							toplevelcomments.add(ci);
					}
				}
			}
		}
		
	}

	private String getHostname()
	{
		return hostnameitem.getHostname();
	}

	public boolean isValid() // the URL that created this Thread was valid
	{
		return valid;
	}

	private JSONArray getTopLevelCommentIds() // get a jsonarray of commentids from the commentitems
	{
		TreeSet<String> comment_ids = new TreeSet<String>();
		CommentItem currentcomment = null;
		Iterator<CommentItem> ci_it = toplevelcomments.iterator();
		while(ci_it.hasNext())
		{
			currentcomment = ci_it.next();
			comment_ids.add(currentcomment.getId());
		}
		return new JSONArray(comment_ids);
	}

	public JSONArray getHPQSPsJSONArray()
	{
		return new JSONArray(hpqsps);
	}

	public long getThreadViews(String inc_significant_designation, int minutes_ago)
	{
		// set up an expression to query hostname + msfe (all)
        DynamoDBQueryExpression<ThreadViewItem> queryExpression = new DynamoDBQueryExpression<ThreadViewItem>()
        		.withIndexName("significant_designation-msfe-index")
				.withScanIndexForward(true)
				.withConsistentRead(false);
        
        // set the significant_designation part
        ThreadViewItem key = new ThreadViewItem();
        key.setSignificantDesignation(inc_significant_designation);
        queryExpression.setHashKeyValues(key);

        // set the msfe range part
        if(minutes_ago > 0)
        {
        	Calendar cal = Calendar.getInstance();
        	cal.add(Calendar.MINUTE, (minutes_ago * -1));
        	long msfe_cutoff = cal.getTimeInMillis();
	        // set the msfe range part
	        Map<String, Condition> keyConditions = new HashMap<String, Condition>();
	    	keyConditions.put("msfe",new Condition()
	    	.withComparisonOperator(ComparisonOperator.GT)
			.withAttributeValueList(new AttributeValue().withN(new Long(msfe_cutoff).toString())));
			queryExpression.setRangeKeyConditions(keyConditions);
        }
        
		// execute
        List<ThreadViewItem> items = mapper.query(ThreadViewItem.class, queryExpression, dynamo_config);
        return items.size();	
	}

	public JSONObject getTopAndBottom()
	{
		//System.out.println("Thread.getTopAndBottom(): entering");
		String top = "";
		String bottom = "";
		JSONObject return_jo = new JSONObject();
		try
		{
			Calendar cal = Calendar.getInstance();
			long now = cal.getTimeInMillis();
			long yearago = now - 31556901000L;
			long monthago = now - 2592000000L;
			long weekago = now - 604800000;
			long dayago = now - 86400000;
			//System.out.println("Thread.getTopAndBottom(): agos: " + yearago + " " + monthago + " " + weekago + " " + dayago);
			long millisecondsintheyearportion = monthago - yearago;
			long millisecondsinthemonthportion = weekago - monthago;
			long millisecondsintheweekportion = dayago - weekago;
			long millisecondsinthedayportion = now - dayago;
			//System.out.println("Thread.getTopAndBottom(): portions: " + millisecondsintheyearportion + " " + millisecondsinthemonthportion + " " + millisecondsintheweekportion + " " + millisecondsinthedayportion);
			double yearportionweight = (double)millisecondsintheyearportion / 31556901000.0;
			double monthportionweight = (double)millisecondsinthemonthportion / 31556901000.0;
			double weekportionweight = (double)millisecondsintheweekportion / 31556901000.0;
			double dayportionweight = (double)millisecondsinthedayportion / 31556901000.0;
			//System.out.println("Thread.getTopAndBottom(): weights: " + yearportionweight + " " + monthportionweight + " " + weekportionweight + " " + dayportionweight);
			long hitsintheyearportion = 0;
			long hitsinthemonthportion = 0;
			long hitsintheweekportion = 0;
			long hitsinthedayportion = 0;
			long currenttimestamp = 0;
			Iterator<CommentItem> c_it = allcomments.iterator();
			CommentItem currentcomment = null;
			while(c_it.hasNext())
			{
				//System.out.println("Thread.getTopAndBottom(): looping comment");
				currentcomment = c_it.next();
				currenttimestamp = currentcomment.getMSFE();
				if (currenttimestamp > dayago && currenttimestamp < now) // this comment happened between now and 24 hours ago
					hitsinthedayportion++;
				else if (currenttimestamp > weekago && currenttimestamp < dayago)
					hitsintheweekportion++;
				else if (currenttimestamp > monthago && currenttimestamp < weekago)
					hitsinthemonthportion++;
				else if (currenttimestamp > yearago && currenttimestamp < monthago)
					hitsintheyearportion++;
			}
			//System.out.println("Thread.getTopAndBottom(): hits: " + hitsintheyearportion + " " + hitsinthemonthportion + " " + hitsintheweekportion + " " + hitsinthedayportion);
			double dayscore = hitsinthedayportion / dayportionweight;
			double weekscore = hitsintheweekportion / weekportionweight;
			double monthscore = hitsinthemonthportion / monthportionweight;
			double yearscore = hitsintheyearportion / yearportionweight;
			//System.out.println("Thread.getTopAndBottom(): scores" + yearscore + " " + monthscore + " " + weekscore + " " + dayscore);
			if(dayscore > weekscore && dayscore > monthscore && dayscore > yearscore)
			{
				top = new Long(hitsinthedayportion).toString();
				bottom = "24h";
			}
			if(weekscore > dayscore && weekscore > monthscore && weekscore > yearscore)
			{
				top = new Long(hitsintheweekportion).toString();
				bottom = "7d";
			}
			if(monthscore > weekscore && monthscore > dayscore && monthscore > yearscore)
			{
				top = new Long(hitsinthemonthportion).toString();
				bottom = "30d";
			}
			if(yearscore > weekscore && yearscore > monthscore && yearscore > dayscore)
			{
				top = new Long(hitsintheyearportion).toString();
				bottom = "1Y";
			}
			return_jo.put("top", top);
			return_jo.put("bottom", bottom);
		}
		catch(JSONException jsone)
		{
			jsone.printStackTrace();
			System.err.println(jsone.getMessage());
		}
		return return_jo;
	}
	
	public JSONObject getJSONObjectRepresentation(boolean getpagetitle, AmazonDynamoDBClient client)    
	{						
		// This function gets the JSON representation of this thread in one of two forms:
		// 1. The hostname is combined and returns all comments attached to all hpqsps
		// 2. The hostname is separated and returns only the comments attached to this hpqsp
		//     ^--- this determination is made in the constructor of this Thread constructor

		JSONObject jsonresponse = new JSONObject();
		if(!valid)
		{
			return null;
		}

		try
		{
			// does the hostname even exist? If not, there is nothing for this thread.

			// this seems dangerously slow. TODO Check where, if any, this function is being called with getpagetitle == true
			if(getpagetitle)
			{
				String page_title = getTitleFromWebPage(original_url);
				if(page_title != null && !page_title.equals("unknown"))
					jsonresponse.put("page_title", page_title);
			}

			if(hostnameitem == null)
			{
				//System.out.println("hostnameitem is null");
				jsonresponse.put("combined_or_separated", "combined");
				jsonresponse.put("original_url", original_url);
				String sd =  Global.getStandardizedHostnameFromURL(original_url);
				jsonresponse.put("significant_designation", sd);
				jsonresponse.put("threadviews", getThreadViews(sd,0));
				jsonresponse.put("numlikes", numlikes);
				if(allcomments != null)
					jsonresponse.put("numcomments", allcomments.size());
				else
					jsonresponse.put("numcomments", 0);
				jsonresponse.put("hostname", Global.getStandardizedHostnameFromURL(original_url));
				jsonresponse.put("hp", Global.getStandardizedHPFromURL(original_url));
				//jsonresponse.put("hpqsp", Global.getStandardizedHPQSPFromURL(original_url));
				return jsonresponse;
			}

			if(allcomments != null)
				jsonresponse.put("numcomments", allcomments.size());
			else
				jsonresponse.put("numcomments", 0);
			
			if(toplevelcomments != null && !toplevelcomments.isEmpty())
			{
				JSONArray master_comment_array = new JSONArray();
				master_comment_array = getTopLevelCommentIds(); // just return the comment ids, no children, no fluff
				jsonresponse.put("children", master_comment_array);
				JSONObject tempjo = getTopAndBottom();
				jsonresponse.put("top", tempjo.getString("top"));
				jsonresponse.put("bottom", tempjo.getString("bottom"));
			}

			if(!hostnameitem.getSeparated())
			{
				jsonresponse.put("combined_or_separated", "combined");
				String sd =  getHostname();
				jsonresponse.put("significant_designation", sd);
				jsonresponse.put("threadviews", getThreadViews(sd,0));
			}
			else
			{
				jsonresponse.put("combined_or_separated", "separated");
				if(hps != null && !hps.isEmpty())
				{ 
					//System.out.println("Separated hostname hps is not null and not empty. Getting hpqspaccordingtoHP as sigdef from url=" + original_url);
					HPItem onlyhp = hps.iterator().next(); 
					String sd = onlyhp.getHPQSPStringAccordingToThisHP(original_url);
					jsonresponse.put("significant_designation", sd);
					jsonresponse.put("threadviews", getThreadViews(sd,0));
				}
				else // if separated and no HP, then return whatever the HP would be (ignoring query string parameters, which are understood to be insignificant)
				{
					//System.out.println("Separated hostname, hps null or empty. Getting stdhp as sigdef from url=" + original_url + " " + Global.getStandardizedHPFromURL(original_url));
					String sd = Global.getStandardizedHPFromURL(original_url); // FIXME does this need a trailing "?" Shouldn't a significant designation be EITHER a hostname OR an HPQSP? Never an HP?
					jsonresponse.put("significant_designation", sd);
					jsonresponse.put("threadviews", getThreadViews(sd,0));
				}
			}
			//System.out.println("Thread.getJSONObjectRepresentation(): adding hostname, hp, std_url and original_url to response object." + original_url);
			jsonresponse.put("numlikes", numlikes);
			jsonresponse.put("hostname", Global.getStandardizedHostnameFromURL(original_url));
			jsonresponse.put("hp", Global.getStandardizedHPFromURL(original_url));
			jsonresponse.put("original_url", original_url);

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		return jsonresponse;
	}

	private String getTitleFromWebPage(String inc_url)
	{
		String title = "";
        HttpClient httpclient = new DefaultHttpClient();
        try {
            HttpGet httpget = new HttpGet(inc_url);

            // Create a response handler
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            String responseBody = httpclient.execute(httpget, responseHandler);
            //System.out.println("----------------------------------------");
            //System.out.println(responseBody);
            //System.out.println("----------------------------------------");

                HTMLEditorKit kit = new HTMLEditorKit(); 
                try 
                {
                    HTMLDocument doc = new HTMLDocument();
                    doc.putProperty("IgnoreCharsetDirective", new Boolean(true));
                    kit.read(new StringReader(responseBody), doc, 0);
                    title = (String) doc.getProperty(Document.TitleProperty);
                //    System.out.println("HTMLDocument Title: " + title);
                } catch (Exception e) 
                {
                    System.err.println("Unexpected " + e + " thrown");
                    return "unknown";
                }
        } 
        catch(ClientProtocolException cpe)
        {
        	System.err.println(cpe.getMessage());
        	return "unknown";
        }
        catch(IOException ioe)
        {
        	System.err.println(ioe.getMessage());
        	return "unknown";
        }
        finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            httpclient.getConnectionManager().shutdown();
        }
        return title;
	}
}