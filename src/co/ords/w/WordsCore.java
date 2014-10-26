
package co.ords.w;

import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Calendar;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

public class WordsCore {

	private WordsMapper mapper;
	private DynamoDBMapperConfig dynamo_config;
	private AmazonDynamoDBClient client;
	
	public WordsCore(WordsMapper inc_mapper, DynamoDBMapperConfig inc_dynamo_config, AmazonDynamoDBClient inc_client)
	{
		client = inc_client;
		mapper = inc_mapper;
		dynamo_config = inc_dynamo_config;
	}
	
	public JSONObject getMetricData(int days)
	{
		JSONObject jsonresponse = new JSONObject();
		
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, (days*-1));
		long cutoff_in_msfe = cal.getTimeInMillis(); //FIXME implement this
		JSONObject metric_data = new JSONObject();
		try
		{
			/*
			 * What we're trying to do here is compose a giant "metric_data" jsonobject
			 * which has a jsonarray of x coordinates
			 * and for each of those, a bunch of y coordinates for each metric item, threadviews, active30, commented7, etc
			 * Additionally, these y coordinates should include "ssi-join_email-facebook-30", etc
			 */
			/*
			// metric_data looks like this
			{
			 	"id": ["14ab4ca4bc22222", "14ab4ca4bc4c42bca",...]
			 	"threadviews": [13213412, 132412341, 12341366172, 13276251234,...]
			 	...
			}
			 */
			
			ScanRequest scanRequest = new ScanRequest()
		    .withTableName("words_metrics");
			ScanResult result = client.scan(scanRequest);
			
			TreeSet<String> master_keyset = new TreeSet<String>();
			// loop through first just to get all the necessary keys
			boolean key_added = false;
			
			for (Map<String, AttributeValue> row : result.getItems()){
				
				if((new Long(row.get("msfe").getN()).longValue() > cutoff_in_msfe))  // only process this row if within window
				{	
					for (Map.Entry<String, AttributeValue> column : row.entrySet()) {
			            String attributeName = column.getKey();
			            key_added = master_keyset.add(attributeName);
			           // if(key_added == true && attributeName.indexOf("registered") != -1)
			            //	System.out.println("Added " + attributeName);
			            JSONArray temp_ja = new JSONArray();
			            if(attributeName.indexOf("coinbase") == -1 && attributeName.indexOf("opera") == -1 && attributeName.indexOf("producthunt") == -1 
		            			&& attributeName.indexOf("sqspthreadviews") == -1 && attributeName.indexOf("emptythreadviews") == -1 && attributeName.indexOf("separatedthreadviews") == -1
		            			&& attributeName.indexOf("-hn-") == -1 && attributeName.indexOf("-reddit-") == -1 && attributeName.indexOf("gmail") == -1) // don't return this info for now // FIXME later
			            	metric_data.put(attributeName, temp_ja);
					}
				}
			}
			/*Iterator<String> it = metric_data.keys();
			while(it.hasNext())
				System.out.println(it.next());*/
			
			TreeSet<String> copy_of_master_keyset = null;
			for (Map<String, AttributeValue> row : result.getItems()){
				copy_of_master_keyset = new TreeSet<String>();
				copy_of_master_keyset.addAll(master_keyset);
				//System.out.println("copy_of_master_keyset.size()=" + copy_of_master_keyset.size());
				if((new Long(row.get("msfe").getN()).longValue() > cutoff_in_msfe)) // only process this row if within window
				{	
					for (Map.Entry<String, AttributeValue> column : row.entrySet()) {
			            String attributeName = column.getKey();
			            AttributeValue value = column.getValue();
			            
			            if(attributeName.equals("id") || attributeName.equals("timestamp_hr"))
			            	metric_data.getJSONArray(attributeName).put(value.getS());
			            else
			            {
			            	if(attributeName.indexOf("coinbase") == -1 && attributeName.indexOf("opera") == -1 && attributeName.indexOf("producthunt") == -1 
			            			&& attributeName.indexOf("sqspthreadviews") == -1 && attributeName.indexOf("emptythreadviews") == -1 && attributeName.indexOf("separatedthreadviews") == -1
			            			&& attributeName.indexOf("-hn-") == -1 && attributeName.indexOf("-reddit-") == -1 && attributeName.indexOf("gmail") == -1) // don't return this info for now // FIXME later
			            		metric_data.getJSONArray(attributeName).put(new Long(value.getN()).longValue());
			            }
			            copy_of_master_keyset.remove(attributeName);
			        }
					// if we missed some columns, add them here
					Iterator<String> remaining_keys_iterator = copy_of_master_keyset.iterator();
					String remaining_key = null;
					while(remaining_keys_iterator.hasNext())
					{
						remaining_key = remaining_keys_iterator.next();
						//System.out.println("remaining_key=" + remaining_key);
						if(remaining_key.indexOf("coinbase") == -1 && remaining_key.indexOf("opera") == -1 && remaining_key.indexOf("producthunt") == -1 
		            			&& remaining_key.indexOf("sqspthreadviews") == -1 && remaining_key.indexOf("emptythreadviews") == -1 && remaining_key.indexOf("separatedthreadviews") == -1
		            			&& remaining_key.indexOf("-hn-") == -1 && remaining_key.indexOf("-reddit-") == -1 && remaining_key.indexOf("gmail") == -1) // don't return this info for now // FIXME later
							metric_data.getJSONArray(remaining_key).put(0);
					}
				}
			}
			
			 jsonresponse.put("response_status", "success");
			 jsonresponse.put("metric_data", metric_data);
			 //System.out.println(jsonresponse);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return jsonresponse;
	}
	
	public JSONObject getMostLikedPages(int num_hours, String hostname)
	{
		//System.out.println("WC.getMostLikedPages(hours, possibleurl) begin");
		JSONObject jsonresponse = new JSONObject();
		 // This method will quickly get taxing on the system BUTTTTT.....
		 // It is a generic method that is the same for everyone, which means it can be cached in the future. 
		
		try
		{
			// test to see if hostname was specified in order to maybe get "trending on this site" items. If so, 
			// 1. Is the hostname even in the system? If not, we know there are no "trending on this site" items.
			// 2. Is the hostname separated? If not, then we know there are no other "trending on this site" items.
			HostnameItem hi = null;
			if(hostname != null)
			{
				hi = mapper.load(HostnameItem.class, hostname, dynamo_config);
				if(hi == null)
				{
					jsonresponse.put("response_status", "success"); // hostname not in system, no results, but successful anyway
					return jsonresponse;
				}
				else if(hi.getSeparated() == false)
				{
					jsonresponse.put("response_status", "success"); // hostname not separated, no results, but successful anyway
					return jsonresponse;
				}
				// if we've gotten here, the hostname is in the system and is separated

				long cutoff_in_msfe = 0L;
				
				// get the MSFE cutoffs
				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.HOUR, num_hours * -1);
				cutoff_in_msfe = cal.getTimeInMillis();
				
				int totalitems = 0;
					
				TreeSet<HPQSPLikeItem> hpqsp_likes = hi.getHPQSPLikes(num_hours*60, mapper, dynamo_config);// getHPQSPLikes takes a minutes value
					
				Map<String, SortableJSONObject> hpqsps = new HashMap<String, SortableJSONObject>();
				SortableJSONObject temp_jo = null;
				int temp_count = 0;
				if(hpqsp_likes != null)
				{	
					Iterator<HPQSPLikeItem> hpqsplikes_it = hpqsp_likes.iterator();
					HPQSPLikeItem hpqsplikeitem = null;
					while(hpqsplikes_it.hasNext())
					{	
						hpqsplikeitem = hpqsplikes_it.next();
						if(hpqsplikeitem.getMSFE() > cutoff_in_msfe)
						{
							if(hpqsps.containsKey(hpqsplikeitem.getHPQSP())) // if the key already exists, just up the count by 1
							{
								temp_jo = hpqsps.get(hpqsplikeitem.getHPQSP());
								temp_count = temp_jo.getInt("count");
								temp_jo.put("count", (temp_count + 1));
								hpqsps.put(hpqsplikeitem.getHPQSP(), temp_jo);
							}
							else // if it doesn't, then create a new jsonobject on that key with a count = 1
							{
								temp_jo = new SortableJSONObject();
								temp_jo.put("count", 1);
								temp_jo.put("page_title", hpqsplikeitem.getPageTitle());
								if(hpqsplikeitem.getURLWhenCreated().startsWith("https://"))
								{
									if(hpqsplikeitem.getHPQSP().endsWith("?"))
										temp_jo.put("pseudo_url", "https://" + hpqsplikeitem.getHPQSP().substring(0,hpqsplikeitem.getHPQSP().length()-1));
									else
										temp_jo.put("pseudo_url", "https://" + hpqsplikeitem.getHPQSP());
								}
								else
								{
									if(hpqsplikeitem.getHPQSP().endsWith("?"))
										temp_jo.put("pseudo_url", "http://" + hpqsplikeitem.getHPQSP().substring(0,hpqsplikeitem.getHPQSP().length()-1));
									else
										temp_jo.put("pseudo_url", "http://" + hpqsplikeitem.getHPQSP());
								}
								//temp_jo.put("url_when_created", ti.getURLWhenCreated()); // insecure to return this?
								hpqsps.put(hpqsplikeitem.getHPQSP(), temp_jo);
							}
						}
					}
				}
				List<SortableJSONObject> sortable_jos = new ArrayList<SortableJSONObject>();
				
				for (Map.Entry<String, SortableJSONObject> entry : hpqsps.entrySet()) {
					String key = entry.getKey();
					SortableJSONObject jo = entry.getValue();
					jo.put("hpqsp", key);
					sortable_jos.add(jo);
				}
					 
				Collections.reverse(sortable_jos);
				Iterator<SortableJSONObject> it = sortable_jos.iterator();
				JSONArray return_ja = new JSONArray();
				int i = 0;
				while(it.hasNext() && i < 5)
				{
					return_ja.put(it.next());
					i++;
				}
					 
				jsonresponse.put("response_status", "success");
				jsonresponse.put("trendingactivity_ja", return_ja);
				jsonresponse.put("num_hours", num_hours); // pass this back to the frontend so we can show the user how many hours (dicated by global db variable) we got
				jsonresponse.put("items_processed", totalitems);
			
				//System.out.println("WC.getMostLikedPages(hours, possibleurl): returning jsonresponse=" + jsonresponse);
			}
			else // hostname was null. This is a generic request, internet-wide
			{
				// whether TTL has expired or not, the first order of business is to return the current snapshot
				GlobalvarItem gvi2 = mapper.load(GlobalvarItem.class, "most_liked_snapshot", dynamo_config);
				if(gvi2 != null)
				{
					jsonresponse = new JSONObject(gvi2.getStringValue());
				}
				else // in the special beginning case where most_active_snapshot hasn't been created, return error
				{
					 jsonresponse.put("message", "Most liked snapshot didn't exist. Wait a moment and try again. If you still see this error, something is wrong.");
					 jsonresponse.put("response_status", "error");
				}
				
				long now = System.currentTimeMillis();
				GlobalvarItem gvi = mapper.load(GlobalvarItem.class, "most_liked_snapshot_ts", dynamo_config);
				if(gvi != null)
				{
					long last_snapshot_ts = gvi.getNumberValue();
					if((now - last_snapshot_ts) > 60000) // minutely
					{
						// FIXME this really should use a lock under heavy use
						// TTL has expired, create snapshot
						MostLikedSnapshotter mas = new MostLikedSnapshotter(num_hours, mapper, dynamo_config);
						mas.start();
					}
				}
				else
				{
					System.err.println("WC: Couldn't find most_liked_snapshot_ts, so MostLikedSnapshotter couldn't start. Create the variable.");
				}
			}
		}
		catch(JSONException jsone)
		{
			System.err.println("JSONException in WC.getMostLikedPages(hours, possibleurl): jsone.getMessage()=" + jsone.getMessage());
			jsone.printStackTrace();
			return null;
		}
		return jsonresponse;
	}
	
	public JSONObject getMostActivePages(int num_hours, String hostname)
	{
		//System.out.println("WC.getMostActivePages(hours, possibleurl) begin");
		JSONObject jsonresponse = new JSONObject();
		 // This method will quickly get taxing on the system BUTTTTT.....
		 // It is a generic method that is the same for everyone, which means it can be cached in the future. 
		
		try
		{
			// test to see if hostname was specified in order to maybe get "trending on this site" items. If so, 
			// 1. Is the hostname even in the system? If not, we know there are no "trending on this site" items.
			// 2. Is the hostname separated? If not, then we know there are no other "trending on this site" items.
			HostnameItem hi = null;
			if(hostname != null)
			{
				hi = mapper.load(HostnameItem.class, hostname, dynamo_config);
				if(hi == null)
				{
					jsonresponse.put("response_status", "success"); // hostname not in system, no results, but successful anyway
					return jsonresponse;
				}
				else if(hi.getSeparated() == false)
				{
					jsonresponse.put("response_status", "success"); // hostname not separated, no results, but successful anyway
					return jsonresponse;
				}
				// if we've gotten here, the hostname is in the system and is separated

				long cutoff_in_msfe = 0L;
				
				// get the MSFE cutoffs
				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.HOUR, num_hours * -1);
				cutoff_in_msfe = cal.getTimeInMillis();
				
				int totalitems = 0;
					
				TreeSet<CommentItem> comments = hi.getAllComments(num_hours*60, mapper, dynamo_config); // getComments takes a minutes value
				
				Map<String, SortableJSONObject> hpqsps = new HashMap<String, SortableJSONObject>();
				SortableJSONObject temp_jo = null;
				int temp_count = 0;
				if(comments != null)
				{	
					Iterator<CommentItem> comments_it = comments.iterator();
					CommentItem ci = null;
					while(comments_it.hasNext())
					{	
						ci = comments_it.next();
						if(ci.getMSFE() > cutoff_in_msfe)
						{
							if(hpqsps.containsKey(ci.getHPQSP())) // if the key already exists, just up the count by 1
							{
								temp_jo = hpqsps.get(ci.getHPQSP());
								temp_count = temp_jo.getInt("count");
								temp_jo.put("count", (temp_count + 1));
								hpqsps.put(ci.getHPQSP(), temp_jo);
							}
							else // if it doesn't, then create a new jsonobject on that key with a count = 1
							{
								temp_jo = new SortableJSONObject();
								temp_jo.put("count", 1);
								temp_jo.put("page_title", ci.getPageTitle());
								if(ci.getURLWhenCreated().startsWith("https://"))
								{
									if(ci.getHPQSP().endsWith("?"))
										temp_jo.put("pseudo_url", "https://" + ci.getHPQSP().substring(0,ci.getHPQSP().length()-1));
									else
										temp_jo.put("pseudo_url", "https://" + ci.getHPQSP());
								}
								else
								{
									if(ci.getHPQSP().endsWith("?"))
										temp_jo.put("pseudo_url", "http://" + ci.getHPQSP().substring(0,ci.getHPQSP().length()-1));
									else
										temp_jo.put("pseudo_url", "http://" + ci.getHPQSP());
								}
								//temp_jo.put("url_when_created", ti.getURLWhenCreated()); // insecure to return this?
								hpqsps.put(ci.getHPQSP(), temp_jo);
							}
						}
					}
				}
				List<SortableJSONObject> sortable_jos = new ArrayList<SortableJSONObject>();
				
				for (Map.Entry<String, SortableJSONObject> entry : hpqsps.entrySet()) {
					String key = entry.getKey();
					SortableJSONObject jo = entry.getValue();
					jo.put("hpqsp", key);
					sortable_jos.add(jo);
				}
					 
				Collections.reverse(sortable_jos);
				Iterator<SortableJSONObject> it = sortable_jos.iterator();
				JSONArray return_ja = new JSONArray();
				int i = 0;
				while(it.hasNext() && i < 5)
				{
					return_ja.put(it.next());
					i++;
				}
					 
				jsonresponse.put("response_status", "success");
				jsonresponse.put("trendingactivity_ja", return_ja);
				jsonresponse.put("num_hours", num_hours); // pass this back to the frontend so we can show the user how many hours (dicated by global db variable) we got
				jsonresponse.put("items_processed", totalitems);
			
				//System.out.println("WC.getMostActivePages(hours, possibleurl): returning jsonresponse=" + jsonresponse);
			}
			else // hostname was null. This is a generic request, internet-wide
			{
				// whether TTL has expired or not, the first order of business is to return the current snapshot
				GlobalvarItem gvi2 = mapper.load(GlobalvarItem.class, "most_active_snapshot", dynamo_config);
				if(gvi2 != null)
				{
					jsonresponse = new JSONObject(gvi2.getStringValue());
				}
				else // in the special beginning case where most_active_snapshot hasn't been created, return error
				{
					jsonresponse.put("message", "Most active snapshot didn't exist. Wait a moment and try again. If you still see this error, something is wrong.");
					jsonresponse.put("response_status", "error");
				}
				
				long now = System.currentTimeMillis();
				GlobalvarItem gvi = mapper.load(GlobalvarItem.class, "most_active_snapshot_ts", dynamo_config);
				if(gvi != null)
				{
					long last_snapshot_ts = gvi.getNumberValue();
					if((now - last_snapshot_ts) > 60000) // minutely
					{
						// FIXME this really should use a lock under heavy use
						// TTL has expired, create snapshot
						MostActiveSnapshotter mas = new MostActiveSnapshotter(num_hours, mapper, dynamo_config);
						mas.start();
					}
				}
				else
				{
					System.err.println("WC: Couldn't find most_active_snapshot_ts, so MostLikedSnapshotter couldn't start. Create the variable.");
				}
			}
		}
		catch(JSONException jsone)
		{
			System.err.println("JSONException in WC.getMostActivePages(hours, possibleurl): jsone.getMessage()=" + jsone.getMessage());
			jsone.printStackTrace();
			return null;
		}
		return jsonresponse;
	}
	
	public JSONObject createUser(String email, String screenname, String password, String picture, String login_type, String useragent, String ip_address)
	{
		//System.out.println("wc.createUser() picture=" + picture);
		JSONObject jsonresponse = new JSONObject();
		boolean screenname_available = false;
		boolean email_available = false;
		try
		{
			// 1. check to see if screenname is taken
			UserItem u = mapper.getUserItemFromScreenname(screenname);
			screenname_available = (u == null) ? true : false;

			// 2. 
			UserItem e = mapper.getUserItemFromEmail(email);
			email_available = (e == null) ? true : false;

			if(screenname_available && !email_available)
			{
				jsonresponse.put("response_status", "error");
				jsonresponse.put("message", "Email unavailable.");
			}
			else if(!screenname_available && email_available)
			{
				jsonresponse.put("response_status", "error");
				jsonresponse.put("message", "Screenname unavailable");
			}
			else if(!screenname_available && !email_available)
			{
				jsonresponse.put("response_status", "error");
				jsonresponse.put("message", "both email and screenname unavailable");
			}
			else // all good
			{
				 UserItem useritem = new UserItem();
				 String uuid_str = UUID.randomUUID().toString().replaceAll("-","");
				 useritem.setId(uuid_str);
				 useritem.setEmail(email.toLowerCase()); // always lowercase when storing emails in the database. ALWAYS
				 useritem.setScreenname(screenname);
				 useritem.setLastIPAddress(ip_address);
				 
				 SHARight sr = new SHARight();
				 String salt = "";
				 try{
					 salt = sr.getSalt();
				 }
				 catch(NoSuchAlgorithmException nsae)
				 {
					 jsonresponse.put("response_status", "error");
					 jsonresponse.put("message", "No such encryption algorithm exception.");
					 return jsonresponse;
				 }	
				 String secpass = sr.get_SHA_512_SecurePassword(password, salt);
				 useritem.setEncryptedPassword(secpass);
				 useritem.setSalt(salt);
				 
				 // if they've logged in with google/facebook we know their email address is legit
				 if(login_type.equals("google") || login_type.equals("facebook"))
					 useritem.setEmailIsConfirmed(true);
				 else
					 useritem.setEmailIsConfirmed(false); // otherwise, they've been given a blah@words4chrome.com address and it is not confirmed
				 
				 useritem.setLastLoginType(login_type);
				 // get browser from useragent
				 useritem.setLastUserAgent(useragent);
				 if(useragent.indexOf("OPR/") != -1)
					 useritem.setLastBrowser("opera");
				 else
					 useritem.setLastBrowser("chrome");
				 useritem.setPicture(picture);
				 	
				 Calendar cal = Calendar.getInstance();
				 long now = cal.getTimeInMillis();
				 useritem.setSince(now);
				 useritem.setSeen(now);
				 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
				 sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));

				 useritem.setSinceHumanReadable(sdf.format(cal.getTimeInMillis()));
				 useritem.setSeenHumanReadable(sdf.format(cal.getTimeInMillis()));
				 String this_access_token = UUID.randomUUID().toString().replaceAll("-","");
				 useritem.setThisAccessToken(this_access_token);
				 long this_access_token_expires = now + 604800000L; // one week from now
				 useritem.setThisAccessTokenExpires(this_access_token_expires);

				 useritem.setOverlaySize(600);
				 useritem.setPermissionLevel("user"); // "user" or "admin" (everyone is automatically a "user" unless manually changed)
				 
				 useritem.setOnReply("button"); // email, button, button
				 useritem.setOnLike("button");  // email, button, button
				 useritem.setOnDislike("button");  // email, button, button
				 useritem.setOnMention("button");  // email, button, button
				 useritem.setPromos("button"); // email, button, button
				 useritem.setOnFollowComment("button"); // email, button, button
				 useritem.setShowFooterMessages(true);
				
				 useritem.setLastActiveMSFE(now);
				 useritem.setLastActiveHumanReadable(sdf.format(cal.getTimeInMillis()));
				 
				 // don't have to excplicitly set these at user creation time
				/*
				 useritem.setThreadViews(0); // yes, no
				 useritem.setNumCommentsAuthored(0);
				 useritem.setNumLikesAuthored(0);
				 useritem.setNumDislikesAuthored(0);
				 useritem.setUp(0);
				 useritem.setDown(0);
				 useritem.setRating(0);
				 useritem.setRatingTS(System.currentTimeMillis());
				 */
				 
				 mapper.save(useritem); //***** COMMIT

				 jsonresponse.put("response_status", "success");
				 jsonresponse.put("screenname", useritem.getScreenname());
				 jsonresponse.put("this_access_token", this_access_token);
				 jsonresponse.put("this_access_token_expires", this_access_token_expires);
			}
		} 
		catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
		return jsonresponse;
	}

	public TreeSet<UserItem> getFollowingUsers(String significant_designation)
	{
		TreeSet<UserItem> returnval = null;

		FollowItem followKey = new FollowItem();
		followKey.setSignificantDesignation(significant_designation);
        
        DynamoDBQueryExpression<FollowItem> queryExpression = new DynamoDBQueryExpression<FollowItem>()
            .withHashKeyValues(followKey);
        List<FollowItem> follows_query_result = mapper.query(FollowItem.class, queryExpression, dynamo_config);
        if(follows_query_result.size() > 0)
        {
        	returnval = new TreeSet<UserItem>();
        	UserItem u = null;
   	        for (FollowItem followitem : follows_query_result) {
   	        	u = mapper.load(UserItem.class, followitem.getUserId(), dynamo_config);
   	        	if(u != null)
   	        		returnval.add(u);
   	        	else
   	        		System.err.println("words_follows table says there's a user (" + followitem.getUserId() + ") that doesn't seem to exist.");
   	        }
        }
		return returnval;
	}
	
	// if this is a top level comment, parent should be null. Parent will be automatically generated from the provided url
	public JSONObject createComment(UserItem useritem, String text, String url, String remoteAddr, String parent)
	{
		JSONObject jsonresponse = new JSONObject();
		JSONObject message = new JSONObject();
		text = text.replaceAll("\n", "MycRazyLineBreakstringakalsjdlfjasdfasdfadsfads");
		text = Jsoup.parse(text).text(); 
		text = text.replaceAll("MycRazyLineBreakstringakalsjdlfjasdfasdfadsfads", "\n");
		try
		{
			// first, check to see if comment is empty
			if(text.trim().length() == 0)
			{
				jsonresponse.put("message", "Can't create empty comment.");
				jsonresponse.put("response_status", "error");
				return jsonresponse;
			}
			
			int count = text.length() - text.replace("\n", "").length();
			if(count > 10)
			{
				jsonresponse.put("message", "Only 10 line breaks (the enter key character) allowed per comment.");
				jsonresponse.put("response_status", "error");
				return jsonresponse;
			}
		}
		catch (JSONException je) 
		{
			System.err.println("TTU.createAndAddComment() error: JSONException. " + je.getMessage());
			return null;
		} 		

		CommentItem parentcommentitem = null;
		int depth = 1;
		try
		{
			// this area below checks for comment depthlevel. Very difficult to do under the new scheme, so I'm getting rid of it for now.
			// if this is a child comment addition, check for the parent before doing anything.
			if(parent != null) // not toplevel
			{	
				parentcommentitem = mapper.load(CommentItem.class, parent, dynamo_config);
				if(parentcommentitem == null)
				{
					message.put("message", "Could not find parent comment.");
					jsonresponse.put("error", message);
					return jsonresponse;
				}
				else if(parentcommentitem.getHidden())
				{
					message.put("message", "Replies to hidden comments are not allowed.");
					jsonresponse.put("error", message);
					return jsonresponse;
				}
				else
				{
					depth = parentcommentitem.getCommentDepth(mapper, dynamo_config) + 1;
					if(depth >= 7)
					{	
						jsonresponse.put("message", "You've tried to make a 7th level comment, which is not supported.");
						jsonresponse.put("response_status", "error");
						return jsonresponse;
					}
				}
			}
		}
		catch (JSONException je) 
		{
			System.err.println("TTU.createAndAddComment() error: JSONException. " + je.getMessage());
			return null;
		} 		

		if(depth != 1) // if this is a reply (which may come from the notification feed), then use the URL of the parent, not the currentURL.
		{
			url = parentcommentitem.getURLWhenCreated();  
		}
		else // otherwise, we need a valid url passed to this function. Check what was passed, erroring out if it doesn't pass muster.
		{
			 try
			 {
				 if(url != null && !url.isEmpty())
				 {
					 if(url != null && url.indexOf("#") != -1)
						 url = url.substring(0, url.indexOf("#"));
					 if(url != null && (url.indexOf("/", url.indexOf("://") + 3) == -1)) // if of the form http://www.yahoo.com, make it http://www.yahoo.com/ 
							url = url + "/";
					 if(!Global.isValidURLFormation(url, true)) // require proto_colon_slashslash
					 {
						 jsonresponse.put("message", "Invalid URL formation.");
						 jsonresponse.put("response_status", "error");
						 return jsonresponse;
					 }
				 }
				 else
				 {
					 jsonresponse.put("message", "Url was null or empty");
					 jsonresponse.put("response_status", "error");
				 }
			 }
			 catch (JSONException je) 
			 {
				 System.err.println("TTU.createAndAddComment() error: JSONException. " + je.getMessage());
				 return null;
			 }
		}
		
		String hostname_str = Global.getStandardizedHostnameFromURL(url);
		if(hostname_str == null)
		{
			try
			{
				message.put("message", "Could not get valid hostname from url.");
				jsonresponse.put("error", message);
				return jsonresponse;
			}
			catch (JSONException je) 
			{
				System.err.println("TTU.createAndAddComment() error: JSONException. " + je.getMessage());
				return null;
			} 		
		}
		
		HostnameItem hostnameitem = mapper.load(HostnameItem.class, hostname_str, dynamo_config);
		String hp_str = Global.getStandardizedHPFromURL(url);
		HPItem hpitem = mapper.load(HPItem.class, hp_str, dynamo_config);
		
		// if necessary, create hostname
		if(hostnameitem == null)
		{
			hostnameitem = new HostnameItem();
			hostnameitem.setHostname(hostname_str);
			// does this new hostname's url match blah.com/2014/03/22/page.html or blah.com/2014/mar/22/page.html date forms? If so, we can separate it automatically.
			boolean a = Pattern.matches(".*/20\\d\\d/\\D\\D\\D/\\d\\d/.*", hp_str);
			//boolean b = Pattern.matches(".*/20\\d\\d/\\d\\d/\\d\\d/.*", hp_str);
			boolean c = Pattern.matches(".*/20\\d\\d/\\d\\d/.*", hp_str);
			if(a || c)
				hostnameitem.setSeparated(true);
			else
				hostnameitem.setSeparated(false);
			hostnameitem.setOriginalURL(url);
			mapper.save(hostnameitem);
		}

		// if necessary, create hp and add to hostname
		if(hpitem == null)
		{
			// initialize new hp
			hpitem = new HPItem();
			hpitem.setHostname(hostname_str);
			hpitem.setHP(hp_str);
			hpitem.setOriginalURL(url);
			mapper.save(hpitem);
		}
		
		// for hps with no significant QSPs, this will default to HP with ? like cnn.com/article.html?
		// the hps with significant QSPs, this will default to the HPQSP that matches, like youtube.com/watch?a=blah&v=blah  (i.e. alphabetized) (assuming a and v are both significant)
		
		// this means that when WORDS is started for the first time, youtube/watch?v=blah breaks down like this:
		// hostname = www.youtube.com
		// hp = www.youtube.com/watch
		// hpqsp = www.youtube.com/watch?
		// comments' parents = www.youtube.com/watch?
		
		// then, when a significant qsp is added (like "v"), the single hpqsp is deleted and many new ones are made
		// hpqsp = www.youtube.com/watch?v=weezervid, www.youtube.com/watch?v=ninvid, www.youtube.com/watch?v=politicalvid
		// and all the comments that were previously attached to www.youtube.com/watch? are divvied up by their original_url value and assigned a new hpqsp value
		// comments' parents = www.youtube.com/watch?v=weezervid, www.youtube.com/watch?v=ninvid, www.youtube.com/watch?v=politicalvid
		
		String hpqsp_str = hpitem.getHPQSPStringAccordingToThisHP(url);
		//System.out.println("hpqsp_str just before comment creation =" + hpqsp_str);
		HPQSPItem hpqspitem = mapper.load(HPQSPItem.class, hpqsp_str, dynamo_config);
		// if necessary, create hpqsp and add to hp
		if(hpqspitem == null)
		{
			// initialize new hpqsp
			hpqspitem = new HPQSPItem();
			hpqspitem.setHPQSP(hpqsp_str);
			hpqspitem.setHP(hp_str);
			hpqspitem.setHostname(hostname_str);
			hpqspitem.setOriginalURL(url);
			mapper.save(hpqspitem);
		}

		if(parent == null) // toplevel
			parent = hpqsp_str;
		
		Calendar cal = Calendar.getInstance();
		long now = cal.getTimeInMillis();
		String now_str = fromDecimalToBase62(7,now);
		Random generator = new Random(); 
		int r = generator.nextInt(238327); // this will produce numbers that can be represented by 3 base62 digits

		String randompart_str = fromDecimalToBase62(3,r);
		String comment_id = now_str + randompart_str + "C"; 

		CommentItem commentitem = new CommentItem();
		commentitem.setId(comment_id);

		commentitem.setText(text);
		commentitem.setMSFE(now);
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
		sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
		System.out.println("Creating comment: formated date=" + sdf.format(cal.getTimeInMillis()));
		
		commentitem.setTimestampHumanReadable(sdf.format(cal.getTimeInMillis()));
		commentitem.setAuthorId(useritem.getId());
		commentitem.setURLWhenCreated(url);
		commentitem.setHostname(hostname_str);
		commentitem.setHPQSP(hpqsp_str);
		commentitem.setIPAddress(remoteAddr);
		// set title asynchronously below
		commentitem.setHidden(false);
		//commentitem.setDummy(dummy);
		commentitem.setParent(parent); // set parent as the id of the comment or the hpqsp_str
		commentitem.setDepth(depth);
		mapper.save(commentitem); //***** COMMIT
		
		CommentTitler ct = new CommentTitler(commentitem, url, mapper, dynamo_config);
		ct.start();

		// note the timestamp as this user's most recent comment
		useritem.setLastCommentMSFE(now);
		useritem.setLastCommentHumanReadable(sdf.format(cal.getTimeInMillis()));
		mapper.save(useritem);

		// infancy
		if(!(useritem.getEmail().endsWith("@words4chrome.com") || useritem.getEmail().endsWith("@ords.co") || useritem.getEmail().equals("fivedogit@gmail.com")))
		{	
			GlobalvarItem gvi = mapper.load(GlobalvarItem.class, "email_admin_on_comment", dynamo_config);
			if(gvi != null && gvi.getNumberValue() == 1)
			{
				String body = "A user commented on " + url;
				String htmlbody = "A user commented on <a href=\"" + url + "\">" + url + "</a>";
				SimpleEmailer se = new SimpleEmailer("User commented on " + url, body, htmlbody, "info@words4chrome.com", "info@words4chrome.com");
				se.start();
			}
		}
		
		// It's possible for a comment to qualify for reply, mention and follow activity alerts, but we only want to do one, in that order.
		// if this is a reply and the author of the comment is not the same as the author of the parent, add to activty
		
		// if(reply && not replying to self)
		// { 
		//		update button & add user to already_notified
		// }
		// if(users were mentioned)
		// {
		//		while(mentioned_users)
		//		{
		//			if(mentioned user not already mentioned && wants button change on mention)
		//			{
		//				update button & add user to already_notified
		//			}
		//		}
		// }
		// while(users following this hpqsp)
		// {
		//		if(following user not already mentioned && wants button change)
		//		{
		//			update button & add user to already_notified
		//		}
		// }
		
		
		TreeSet<String> already_notified = new TreeSet<String>(); // notified via button, not emailed
		already_notified.add(useritem.getId()); // add user here as "already notified" since no notifications should ever fire to the user who initiated this action.
		
		if(depth > 1 && !parentcommentitem.getAuthorId().equals(useritem.getId()))
		{
			UserItem parentcommentauthor = null;
			parentcommentauthor = mapper.load( UserItem.class, parentcommentitem.getAuthorId(), dynamo_config);
			if(parentcommentauthor != null && parentcommentauthor.getOnReply().equals("button"))
			{
				Set<String> existing_activity_ids = parentcommentauthor.getActivityIds();
				if(existing_activity_ids == null)
					existing_activity_ids = new TreeSet<String>();
				String commentid = commentitem.getId();
				String replyid = commentid.substring(0,10) + "R"; 
				existing_activity_ids.add(replyid);
				parentcommentauthor.setActivityIds(existing_activity_ids);
				int notification_count = parentcommentauthor.getNotificationCount();
				notification_count++;
				parentcommentauthor.setNotificationCount(notification_count);			
				mapper.save(parentcommentauthor);	
				already_notified.add(parentcommentauthor.getId()); 
			}
		}
		
		TreeSet<UserItem> mentioned_users = commentitem.getMentionedUsers(null, mapper); // null means use default text of this commentitem
		if(mentioned_users != null && mentioned_users.size() > 0)
		{	
			//System.out.println("WC.createAndAddComment(): Yes they were.");
			UserItem mentioned_user = null;
			Iterator<UserItem> it = mentioned_users.iterator();
			while(it.hasNext())
			{	
				mentioned_user = it.next();
				if(!already_notified.contains(mentioned_user.getId()) && mentioned_user.getOnMention().equals("button"))
				{
					Set<String> existing_activity_ids = mentioned_user.getActivityIds();
					if(existing_activity_ids == null)
						existing_activity_ids = new TreeSet<String>();
					String commentid = commentitem.getId();
					String mentionid = commentid.substring(0,10) + "M"; 
					existing_activity_ids.add(mentionid);
					mentioned_user.setActivityIds(existing_activity_ids);
					int notification_count = mentioned_user.getNotificationCount();
					notification_count++;
					mentioned_user.setNotificationCount(notification_count);
					mapper.save(mentioned_user);
					already_notified.add(mentioned_user.getId());
				}
			}
		}
			
		//System.out.println("WC.createComment(): Checking for following users.");
		String significant_designation = Global.getHPQSPAccordingToThisURL(url, mapper);
		TreeSet<UserItem> following_users = getFollowingUsers(significant_designation);
		if(following_users != null && following_users.size() > 0)
		{ 
			//System.out.println("WC.createComment(): Found following users for sigdes=" + significant_designation);
			Iterator<UserItem> fu_it = following_users.iterator();
			UserItem following_user = null;
			
			while(fu_it.hasNext())
			{
				following_user = fu_it.next();
				if(!already_notified.contains(following_user.getId()) && following_user.getOnFollowComment().equals("button"))
				{
					Set<String> existing_activity_ids = following_user.getActivityIds();
					if(existing_activity_ids == null)
						existing_activity_ids = new TreeSet<String>();
					String commentid = commentitem.getId();
					String activityid = commentid.substring(0,10) + "F"; 
					existing_activity_ids.add(activityid);
					following_user.setActivityIds(existing_activity_ids);
					int notification_count = following_user.getNotificationCount();
					notification_count++;
					following_user.setNotificationCount(notification_count);
					mapper.save(following_user);
					already_notified.add(following_user.getId());
				}
			}	
		}
		
		if(commentitem.getText().indexOf("#everyone") != -1 && useritem.getPermissionLevel().equals("admin"))
		{
			 DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
			 List<UserItem> scanResult = mapper.scan(UserItem.class, scanExpression);
			 for (UserItem enumerated_user : scanResult) {
				 if(!already_notified.contains(enumerated_user.getId()))
				 { 
					 Set<String> existing_activity_ids = enumerated_user.getActivityIds();
					 if(existing_activity_ids == null)
						 existing_activity_ids = new TreeSet<String>();
					 String commentid = commentitem.getId();
					 String activityid = commentid.substring(0,10) + "M"; 
					 existing_activity_ids.add(activityid);
					 enumerated_user.setActivityIds(existing_activity_ids);
					 int notification_count = enumerated_user.getNotificationCount();
					 notification_count++;
					 enumerated_user.setNotificationCount(notification_count);
					 mapper.save(enumerated_user);
					 // already_notified.add(enumerated_user.getId()); // no need to actually do this. Everyone will be in the list when all asid and done.
				 }
			}
		}
		
		try{
			jsonresponse.put("response_status", "success");
			jsonresponse.put("comment", commentitem.getAsJSONObject(mapper, dynamo_config, false));	// do not get author stuff if hidden
		}
		catch (JSONException je) 
		{
			System.err.println("WC.createAndAddComment() error: JSONException. " + je.getMessage());
		} 	
		return jsonresponse;		
	}

    public JSONObject createCommentLike(UserItem useritem, CommentItem commentitem, String remoteAddr)
   	{  // pre-reqs: user has been verified, thread exists
   		JSONObject return_jo = new JSONObject();
   		if(useritem.getId().equals(commentitem.getAuthorId()))
   		{
   			try{
				//System.out.println("WC.createCommentLike(): User trying to like own comment. ************");
				return_jo.put("response_status", "error");
				return_jo.put("message", "You can't like your own comments.");
			}
			catch(JSONException jsone){ System.err.println("JSONException in WC.createCommentLike() while checking if user has already liked."); }
			return return_jo;
   		}
    	if(useritem.hasLikedComment(commentitem.getId(), mapper, dynamo_config))
    	{
    		try{
				//System.out.println("WC.createCommentLike(): User has already liked this comment. ************");
				return_jo.put("response_status", "error");
				return_jo.put("message", "You've already liked this comment.");
			}
			catch(JSONException jsone){ System.err.println("JSONException in WC.createCommentLike() while checking if user has already liked."); }
			return return_jo;
    	}
    	
    	Calendar cal = Calendar.getInstance();
		long now = cal.getTimeInMillis();
		String now_str = fromDecimalToBase62(7,now);
		Random generator = new Random(); 
		int r = generator.nextInt(238327); // this will produce numbers that can be represented by 3 base62 digits
		String randompart_str = fromDecimalToBase62(3,r);
		String comment_like_id = now_str + randompart_str + "L";

		LikeItem likeitem = new LikeItem();
		likeitem.setId(comment_like_id);
		likeitem.setAuthorId(useritem.getId());
		likeitem.setIPAddress(remoteAddr);
		likeitem.setParent(commentitem.getId());
		likeitem.setMSFE(now);
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
		sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
		
		likeitem.setTimestampHumanReadable(sdf.format(cal.getTimeInMillis()));
		likeitem.setURLWhenCreated(commentitem.getURLWhenCreated());
		likeitem.setHPQSP(Global.getHPQSPAccordingToThisURL(commentitem.getURLWhenCreated(), mapper));
		mapper.save(likeitem); //***** COMMIT

		useritem.setLastLikeMSFE(now);
		useritem.setLastLikeHumanReadable(sdf.format(cal.getTimeInMillis()));
		mapper.save(useritem);
		
		// update parent's activity_feed and notification count ONLY if the two authors are different
		UserItem parentcommentauthor = mapper.load( UserItem.class, commentitem.getAuthorId(),  new DynamoDBMapperConfig(DynamoDBMapperConfig.ConsistentReads.EVENTUAL));
		if(parentcommentauthor.getOnLike().equals("button"))
		{	
			Set<String> existing_activity_ids = parentcommentauthor.getActivityIds();
			if(existing_activity_ids == null)
				existing_activity_ids = new TreeSet<String>();
			existing_activity_ids.add(likeitem.getId());
			parentcommentauthor.setActivityIds(existing_activity_ids);
			int notification_count = parentcommentauthor.getNotificationCount();
			notification_count++;
			parentcommentauthor.setNotificationCount(notification_count);
			mapper.save(parentcommentauthor);
		}
		
		try {
			return_jo.put("response_status", "success");
			return_jo.put("like", likeitem.getJSONObject(mapper, dynamo_config));
			return_jo.put("parent", commentitem.getAsJSONObject(mapper, dynamo_config, false)); // don't get author stuff if hidden
		}catch(JSONException jsone){ System.err.println("JSONException in TTU.createCommentLike() while assembling final return object"); }
		return return_jo;
   	}
    
    public JSONObject createCommentDislike(UserItem useritem, CommentItem commentitem, String remoteAddr)
   	{  // pre-reqs: user has been verified, thread exists
    	JSONObject return_jo = new JSONObject();
    	if(useritem.hasDislikedComment(commentitem.getId(), mapper, dynamo_config))
    	{
    		try{
			//	System.out.println("TTU.createCommentDislike(): User has already disliked this comment. ************");
			return_jo.put("response_status", "error");
			return_jo.put("message", "You've already disliked this comment.");
			}
			catch(JSONException jsone){ System.err.println("JSONException in TTU.createCommentDislike() while checking if user has already disliked."); }
			return return_jo;
    	}
    	
    	//System.out.println("TTU.createCommentDislike(): screenname=" + useritem.getScreennameLiteral() + " commentitem.id=" + commentitem.getId());
    	Calendar cal = Calendar.getInstance();
		long now = cal.getTimeInMillis();
		String now_str = fromDecimalToBase62(7,now);
		Random generator = new Random(); 
		int r = generator.nextInt(238327); // this will produce numbers that can be represented by 3 base62 digits
		String randompart_str = fromDecimalToBase62(3,r);
		String comment_dislike_id = now_str + randompart_str + "D";

		DislikeItem dislikeitem = new DislikeItem();
		dislikeitem.setId(comment_dislike_id);
		dislikeitem.setAuthorId(useritem.getId());
		dislikeitem.setIPAddress(remoteAddr);
		dislikeitem.setParent(commentitem.getId());
		dislikeitem.setMSFE(now);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
		sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
		dislikeitem.setTimestampHumanReadable(sdf.format(cal.getTimeInMillis()));
		dislikeitem.setURLWhenCreated(commentitem.getURLWhenCreated());
		dislikeitem.setHPQSP(Global.getHPQSPAccordingToThisURL(commentitem.getURLWhenCreated(), mapper));
		mapper.save(dislikeitem); //***** COMMIT

		useritem.setLastDislikeMSFE(now);
		useritem.setLastDislikeHumanReadable(sdf.format(cal.getTimeInMillis()));
		mapper.save(useritem);
		
		
		double hideall_threshold = -5.0;
		GlobalvarItem gvi = mapper.load(GlobalvarItem.class, "hideall_threshold", dynamo_config);
		if(gvi != null)
			hideall_threshold = gvi.getNumberValue();
		
		if(useritem.getRatingInWindow() < (hideall_threshold + 1)) // if the user is getting close to the hideall threshold, recalc and hide if necessary
			useritem.getOrCalcRatingInWindow(mapper, dynamo_config, true); 

		// update parent's activity_feed and notification count ONLY if the two authors are different
		UserItem parentcommentauthor = mapper.load( UserItem.class, commentitem.getAuthorId(),  new DynamoDBMapperConfig(DynamoDBMapperConfig.ConsistentReads.EVENTUAL));
		if(parentcommentauthor.getOnDislike().equals("button"))
		{	
			Set<String> existing_activity_ids = parentcommentauthor.getActivityIds();
			if(existing_activity_ids == null)
				existing_activity_ids = new TreeSet<String>();
			existing_activity_ids.add(dislikeitem.getId());
			parentcommentauthor.setActivityIds(existing_activity_ids);
			int notification_count = parentcommentauthor.getNotificationCount();
			notification_count++;
			parentcommentauthor.setNotificationCount(notification_count);
			mapper.save(parentcommentauthor);
		}
		
		try {
			return_jo.put("response_status", "success");
			return_jo.put("dislike", dislikeitem.getJSONObject(mapper, dynamo_config));
			return_jo.put("parent", commentitem.getAsJSONObject(mapper, dynamo_config, false)); // don't get author stuff if hidden
		}catch(JSONException jsone){ System.err.println("JSONException in TTU.createCommentDislike() while assembling final return object"); }
		return return_jo;
   	}
    
    public JSONObject createHPQSPLike(UserItem useritem, String url)
    {
    	JSONObject jsonresponse = new JSONObject();
    	try
    	{
    		if(url == null)
   		 {
   			 jsonresponse.put("response_status", "error");
   			 jsonresponse.put("message", "URL cannot be null.");
   		 }
   		 else
   		 {
   			 if(url != null && url.indexOf("#") != -1)
   				 url = url.substring(0, url.indexOf("#"));
   			 if(url != null && (url.indexOf("/", url.indexOf("://") + 3) == -1)) // if of the form http://www.yahoo.com, make it http://www.yahoo.com/ 
   				url = url + "/";
   			 String hostname_str = Global.getStandardizedHostnameFromURL(url);
   			 if(hostname_str == null)
   			 {
   				 jsonresponse.put("response_status", "error");
   				 jsonresponse.put("message", "Invalid URL.");
   			 }
   			 else 
   			 {
   				 HostnameItem hostnameitem = mapper.load(HostnameItem.class, hostname_str, dynamo_config);
   				 if(hostnameitem == null || !hostnameitem.getSeparated())
   				 {
   					 jsonresponse.put("response_status", "error");
   					 jsonresponse.put("message", "Cannot HPQSPLike a combined hostname.");
   				 }
   				 else
   				 {
   					 String hp_str = Global.getStandardizedHPFromURL(url); // it was a valid URL above (for hostname), so we know this is valid here as well
   					 HPItem hpitem = mapper.load(HPItem.class, hp_str, dynamo_config);
   					 if(hpitem == null)
   					 {
   							// initialize new hp
   							hpitem = new HPItem();
   							hpitem.setHostname(hostname_str);
   							hpitem.setHP(hp_str);
   							hpitem.setOriginalURL(url);
   							mapper.save(hpitem);
   					 }
   					
   					 String hpqsp_str = hpitem.getHPQSPStringAccordingToThisHP(url); // it was a valid URL above (for hostname), so we know this is valid here as well
   					 HPQSPItem hpqspitem = mapper.load(HPQSPItem.class, hpitem.getHPQSPStringAccordingToThisHP(url), dynamo_config);
   					 boolean user_has_already_liked = true;
   					 if(hpqspitem == null) // hp existed, but HPQSP did not. Create it.
   					 {
   						 user_has_already_liked = false; // if the hpqsp doesn't exist, user has obviously not pageliked it yet
   						 hpqspitem = new HPQSPItem();
   						 hpqspitem.setHPQSP(hpqsp_str);
   						 hpqspitem.setHP(hp_str);
   						 hpqspitem.setHostname(hostname_str);
   						 hpqspitem.setOriginalURL(url);
   						 mapper.save(hpqspitem);
   					 }
   					 else // hpqsp already existed. Has user liked it?
   					 {
   						 user_has_already_liked = useritem.hasLikedHPQSP(hpqsp_str, mapper, dynamo_config);
   					 }
   					 
   					 
   					 if(!user_has_already_liked)
   					 {
   						Calendar cal = Calendar.getInstance();
   						long now = cal.getTimeInMillis();
   						String now_str = fromDecimalToBase62(7,now);
   						Random generator = new Random(); 
   						int r = generator.nextInt(238327); // this will produce numbers that can be represented by 3 base62 digits

   						String randompart_str = fromDecimalToBase62(3,r);
   						String pagelike_id = now_str + randompart_str + "P"; // p can stand for "page" or "parameter" (as in hpqsP)

   						HPQSPLikeItem pi = new HPQSPLikeItem();
   						pi.setId(pagelike_id);
   						pi.setAuthorId(useritem.getId());
   						pi.setHostname(hpqspitem.getHostname());
   						pi.setHPQSP(hpqspitem.getHPQSP());
   						//	 set title asynchronously below
   						pi.setURLWhenCreated(url);
   						pi.setMSFE(now);
   						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
   						sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
   						pi.setTimestampHumanReadable(sdf.format(cal.getTimeInMillis()));
   						mapper.save(pi);
						 
   						HPQSPLikeTitler t = new HPQSPLikeTitler(pi, url, mapper, dynamo_config);
   						t.start();
   						
   						useritem.setLastHPQSPLikeMSFE(now);
   						useritem.setLastHPQSPLikeHumanReadable(sdf.format(cal.getTimeInMillis()));
   						mapper.save(useritem);
						 
   						jsonresponse.put("response_status", "success");
   					 }
   					 else
   					 {
   						 jsonresponse.put("response_status", "error");
   						 jsonresponse.put("message", "You have already liked this page.");
   					 }
   					 
   				 }
   			 }
   		 }
    	}catch(JSONException jsone){ System.err.println("JSONException in TTU.createCommentDislike() while assembling final return object"); }
    	return jsonresponse;
    }
    
    public JSONObject createHostnameLike(UserItem useritem, String url)
    {
    	JSONObject jsonresponse = new JSONObject();
    	try
    	{
    		if(url == null)
   		 {
   			 jsonresponse.put("response_status", "error");
   			 jsonresponse.put("message", "URL cannot be null.");
   		 }
   		 else
   		 {
   			 if(url != null && url.indexOf("#") != -1)
   				 url = url.substring(0, url.indexOf("#"));
   			 if(url != null && (url.indexOf("/", url.indexOf("://") + 3) == -1)) // if of the form http://www.yahoo.com, make it http://www.yahoo.com/ 
   				url = url + "/";
   			 String hostname_str = Global.getStandardizedHostnameFromURL(url);
   			 if(hostname_str == null)
   			 {
   				 jsonresponse.put("response_status", "error");
   				 jsonresponse.put("message", "Invalid URL.");
   			 }
   			 else 
   			 {
   				 HostnameItem hostnameitem = mapper.load(HostnameItem.class, hostname_str, dynamo_config);
   				 if(hostnameitem != null && hostnameitem.getSeparated())
   				 {
   					 jsonresponse.put("response_status", "error");
   					 jsonresponse.put("message", "Cannot HostnameLike a separated hostname.");
   				 }
   				 else
   				 {
   					 if(hostnameitem == null)
   					 {
   						hostnameitem = new HostnameItem();
   						hostnameitem.setHostname(hostname_str);
   						// does this new hostname's url match blah.com/2014/03/22/page.html or blah.com/2014/mar/22/page.html date forms? If so, we can separate it automatically.
   						boolean a = Pattern.matches(".*/20\\d\\d/\\D\\D\\D/\\d\\d/.*", url);
   						//boolean b = Pattern.matches(".*/20\\d\\d/\\d\\d/\\d\\d/.*", hp_str);
   						boolean c = Pattern.matches(".*/20\\d\\d/\\d\\d/.*", url);
   						if(a || c)
   							hostnameitem.setSeparated(true);
   						else
   							hostnameitem.setSeparated(false);
   						hostnameitem.setOriginalURL(url);
   						mapper.save(hostnameitem);
   					 }
   					 
   					 boolean user_has_already_liked = useritem.hasLikedHostname(hostname_str, mapper, dynamo_config);
   					 
   					 if(!user_has_already_liked)
   					 {
   						Calendar cal = Calendar.getInstance();
   						long now = cal.getTimeInMillis();
   						String now_str = fromDecimalToBase62(7,now);
   						Random generator = new Random(); 
   						int r = generator.nextInt(238327); // this will produce numbers that can be represented by 3 base62 digits

   						String randompart_str = fromDecimalToBase62(3,r);
   						String pagelike_id = now_str + randompart_str + "H"; // H stands for hostname

   						HostnameLikeItem hli = new HostnameLikeItem();
   						hli.setId(pagelike_id);
   						hli.setAuthorId(useritem.getId());
   						hli.setHostname(hostname_str);
   						hli.setMSFE(now);
   						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
   						sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
   						hli.setTimestampHumanReadable(sdf.format(cal.getTimeInMillis()));
   						mapper.save(hli);
						
   						// trending is specifically for hpqsps (comments, replies and hpqsplikes), not hostnames. do nothing here
   						
   						useritem.setLastHostnameLikeMSFE(now);
   						useritem.setLastHostnameLikeHumanReadable(sdf.format(cal.getTimeInMillis()));
   						mapper.save(useritem);
						 
   						jsonresponse.put("response_status", "success");
   					 }
   					 else
   					 {
   						 jsonresponse.put("response_status", "error");
   						 jsonresponse.put("message", "You have already liked this site.");
   					 }
   					 
   				 }
   			 }
   		 }
    	}catch(JSONException jsone){ System.err.println("JSONException in TTU.createCommentDislike() while assembling final return object"); }
    	return jsonresponse;
    }
    
	private static final String baseDigits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

	/* unused but don't delete, may need later.
	private static long fromOtherBaseToDecimal(int base, String number ) {
		//alert("i "  + base + " and number " + number)
	    int iterator = number.length();
	    long returnValue = 0;
	    long multiplier = 1;
	  //  alert("t " + iterator);
	    while( iterator > 0 ) {
	        returnValue = returnValue + ( baseDigits.indexOf( number.substring( iterator - 1, iterator ) ) * multiplier );
	        multiplier = multiplier * base;
	        --iterator;
	    }
	   // alert("z");
	    return returnValue;
	}*/
    
    private static String fromDecimalToBase62 (int desiredlength, long decimalNumber) {
		long base = 62;
		String tempVal = decimalNumber == 0 ? "0" : "";
        int mod = 0;
        Long modLong = 0L;
        while( decimalNumber != 0 ) {
        	try
        	{
        		modLong = new Long(decimalNumber % base);
        	}
        	catch(Exception e)
        	{
        		System.err.println("WC.fromDecimalToBase62. Error doing decimalNumber % base. returning null");
        		return null;
        	}
            mod = modLong.intValue();
            tempVal = baseDigits.substring( mod, mod + 1 ) + tempVal;
            decimalNumber = decimalNumber / base;
        }

        if(tempVal.length() > desiredlength)
        	return null;
        while(tempVal.length() < desiredlength)
        {
        	tempVal = "0" + tempVal;
        }
        return tempVal;
    }	

   
    
    public JSONObject setSignificantQSPs(String inc_url, String[] significant_qsps) // url must have protocol
	{
    	JSONObject jsonresponse = new JSONObject();
    	try
		{
    		if(!Global.isValidURLFormation(inc_url, true))
    		{
    			System.err.println("WordsCore.setSignificantQSPs(): not a valid url");
    			jsonresponse.put("response_status", "error");
    			jsonresponse.put("message", "Not a valid URL.");
    			return jsonresponse;
    		}
    			
    		String hp_string = Global.getStandardizedHPFromURL(inc_url);
    		//System.out.println("WordsCore.setSignificantQSPs(): Deleting HPQSPLikeItems before performing operation because the current hpqsp will cease to exist... ");
    		DynamoDBScanExpression scanExpression2 = new DynamoDBScanExpression();
    		List<HPQSPLikeItem> scanResult2 = mapper.scan(HPQSPLikeItem.class, scanExpression2);
    		for (HPQSPLikeItem pi : scanResult2) {
    			if(Global.getStandardizedHPFromURL(pi.getURLWhenCreated()).equals(hp_string))
    				mapper.delete(pi);
    		}
    		//System.out.println("done.");
    		
    		HostnameItem hostnameitem = mapper.load(HostnameItem.class, Global.getStandardizedHostnameFromURL(inc_url), dynamo_config);
    		if(hostnameitem == null || !hostnameitem.getSeparated())
    		{
    			System.err.println("WordsCore.setSignificantQSPs(): hostname doesn't exist or is combined. Separate the hostname first.");
    			jsonresponse.put("response_status", "error");
    			jsonresponse.put("message", "Hostname doesn't exist or is combined. Separate the hostname first.");
    			return jsonresponse;
    		}
    		
    		HPItem hpitem = mapper.load(HPItem.class, hp_string, dynamo_config);
    		if(hpitem == null)
    		{
    			// initialize new hp
    			hpitem = new HPItem();
    			hpitem.setHostname(Global.getStandardizedHostnameFromURL(hp_string));
    			hpitem.setHP(hp_string);
    			hpitem.setOriginalURL(inc_url);
    			mapper.save(hpitem);
    		}
    		
    		// in order to set significant QSPs for this hp, let's first check to see if the hp has any signficant QSPs now. 
    		Set<String> existing_significant_qsps = hpitem.getSignificantQSPs();
    		if(existing_significant_qsps != null )
    		{
    			System.err.println("WordsCore.setSignificantQSPs(): Error: Can't set significant QSPs as this HP already has one. Multiples are not supported yet.");
    			jsonresponse.put("response_status", "error");
    			jsonresponse.put("message", "HP already has an sqsp. Can't set multples yet.");
    		}
    		else if(significant_qsps.length > 1)
    		{
    			System.err.println("WordsCore.setSignificantQSPs(): Error: Setting multiple significant qsps at the same time is not supported yet.");
    			jsonresponse.put("response_status", "error");
    			jsonresponse.put("message", "Setting multiple sqps is not supported yet.");
    		}
    		else
    		{
    			// to set a significant QSP for this HP, loop through the comments on this HP
    			// for each comment, look at the original_url value and derive a new "parent" value from it based on the new significant QSP value.
    			// let's say www.youtube.com/watch? has no significant qsps and we want to designate "v" as one
    			// if the original_url value of the comment contains the new significant_qsp, then change the parent value from (ex) www.youtube.com/watch? to www.youtube.com/watch?v=<valuefromparenturl>
    			// if the original_url value of the comment does NOT contains the new sqsp, then change the parent value to "www.youtube.com/watch?v="
    			HPQSPItem hpqspitem = mapper.load(HPQSPItem.class, hpitem.getHP() + "?", dynamo_config);
    			if(hpqspitem == null)
    			{
    				//System.out.println("WordsCore.setSignificantQSPs(): No HPQSP for this url. Setting hp's new sqsp and moving on.");
    				hpitem.setSignificantQSPs(new HashSet<String>(Arrays.asList(significant_qsps)));
    				mapper.save(hpitem);
    				jsonresponse.put("response_status", "success"); // there was no hpqsp for this url yet, so no need to do anything other than set the HP's new sqsp value
    			}
    			else // the hpqsp existed for this url.
    			{	
    				// loop through comments for this hpqsp (www.example.com/somepath?)
    				//	{
    				// 		look at the original_urls for each (www.example.com/somepath?a=1&b=2&significant=3)
    				// 		set comment's new parent value to the new hpqsp with the sqsp value (www.example.com/somepath?significant=3)
    				// 		if the associated hpqsp doesn't exist yet, create it.
    				//	}
    				//	delete original hpqsp (www.example.com/somepath?)
    				//  set hpitem's significant qsp
    				
    				String sqsp_key = significant_qsps[0];
    				TreeSet<CommentItem> commentitems = hpqspitem.getAllComments(0, mapper, dynamo_config); 
    				if(commentitems != null && commentitems.size() > 0)
    				{	
    					CommentItem currentcomment = null;
    					Iterator<CommentItem> ci_it = commentitems.iterator();
    					String current_original_url = "";
    					String new_hpqsp_str = "";
    					while(ci_it.hasNext())
    					{
    						currentcomment = ci_it.next();
    						current_original_url = currentcomment.getURLWhenCreated();
    						System.out.println("*** ORIGINAL *** id - depth - original_url - hpqsp - parent");
    						System.out.println(currentcomment.getId() + " " + currentcomment.getDepth() + " " + currentcomment.getURLWhenCreated() + " " + currentcomment.getHPQSP() + " " + currentcomment.getParent());
    						URL url_object = null;
    						url_object = new URL("http://" + current_original_url);
							String qsp = url_object.getQuery();
							String currentstring = ""; String left = ""; String right="";
							TreeMap<String, String> tmap = new TreeMap<String, String>();
							if(qsp == null)
							{
								tmap.put(sqsp_key,"");
							}
							else
							{	
								StringTokenizer st = new StringTokenizer(qsp, "&");
    							while(st.hasMoreTokens())
    							{
    								currentstring = st.nextToken();
    								if(currentstring.indexOf("=") == -1) // invalid qsp
    								{
    									// no equals sign. Just skip
    								}
    								else
    								{
    									left = currentstring.substring(0,currentstring.indexOf("="));
    									right = currentstring.substring(currentstring.indexOf("=")+1);
    									tmap.put(left,right);
    								}
    							}	
							}
							new_hpqsp_str = hpitem.getHP() + "?" + sqsp_key + "=" + tmap.get(sqsp_key);
    						currentcomment.setHPQSP(new_hpqsp_str);
    						if(currentcomment.getDepth() == 1) // only change the parent if this is a top-level comment
    							currentcomment.setParent(new_hpqsp_str);
    						mapper.save(currentcomment);
    						System.out.println("*** CHANGED  *** id - depth - original_url - hpqsp - parent");
    						System.out.println(currentcomment.getId() + " " + currentcomment.getDepth() + " " + currentcomment.getURLWhenCreated() + " " + currentcomment.getHPQSP() + " " + currentcomment.getParent());
    						
    						// does an hpqsp exist for this new string?
    						HPQSPItem hpqspcheck = mapper.load(HPQSPItem.class, new_hpqsp_str, dynamo_config);
    						if(hpqspcheck == null)
    						{
    							//System.out.println("WordsCore.setSignificantQSPs(): HPQSPItem doesn't exist for " + new_hpqsp_str + ", creating it!");
    							hpqspcheck = new HPQSPItem(); // load all existing values
    							hpqspcheck.setHPQSP(new_hpqsp_str);
    							hpqspcheck.setHP(hpqspitem.getHP());
    							hpqspcheck.setHostname(hpqspitem.getHostname());
    							hpqspcheck.setOriginalURL(hpqspitem.getOriginalURL());
    							mapper.save(hpqspcheck);
    						}
    						else
    						{
    							//System.out.println("HPQSPItem already exists for " + new_hpqsp_str + ", skipping.");
    						}
    					}
    				}
    				// all comments' parents have been rewritten. Delete the original hpqsp item?
    				//System.out.println("Deleting original hpqspitem that we just rearranged. It was " + hpqspitem.getHPQSP());
    				mapper.delete(hpqspitem);
    				hpitem.setSignificantQSPs(new HashSet<String>(Arrays.asList(significant_qsps)));
    				mapper.save(hpitem);
    				jsonresponse.put("response_status", "success");
    			}
    		}
    		return jsonresponse;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return null; // there was no hpqsp for this url yet, so no need to do anything other than set the HP's new sqsp value
		}
	}
    
	public static void main(String [] args)
	{
		AWSCredentials credentials;
		AmazonDynamoDBClient client;
		WordsMapper mapper;
		DynamoDBMapperConfig dynamo_config;
		try {
			credentials = new PropertiesCredentials(WordsCore.class.getClassLoader().getResourceAsStream("AwsCredentials.properties"));
			client = new AmazonDynamoDBClient(credentials);
			mapper = new WordsMapper(client);
			dynamo_config = new DynamoDBMapperConfig(DynamoDBMapperConfig.ConsistentReads.EVENTUAL);
			WordsCore wc = new WordsCore(mapper, dynamo_config, client);
			wc.setSignificantQSPs("http://www.words4chrome.com/", new String[]{"p"});
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	

}