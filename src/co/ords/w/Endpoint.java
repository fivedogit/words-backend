
package co.ords.w;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.Calendar;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.crypto.Data;

import org.apache.commons.validator.routines.EmailValidator;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;


public class Endpoint extends HttpServlet {
	
	// static variables:
	private static final long serialVersionUID = 1L;
	
	private AWSCredentials credentials;
	private AmazonDynamoDBClient client;
	private WordsMapper mapper;
	private DynamoDBMapperConfig dynamo_config;
	private boolean devel = true;
	GlobalvarItem imp_source_categories_gvi = null; // would otherwise be polled on every new impression
	GlobalvarItem imp_targets_gvi = null; // would otherwise be polled on every new impression
	
	public void init(ServletConfig servlet_config) throws ServletException
	{
		try {
			//System.out.println("Initializing DynamoDBMapper from Endpoint.init()");
			credentials = new PropertiesCredentials(getClass().getClassLoader().getResourceAsStream("AwsCredentials.properties"));
			client = new AmazonDynamoDBClient(credentials);
			mapper = new WordsMapper(client);
			dynamo_config = new DynamoDBMapperConfig(DynamoDBMapperConfig.ConsistentReads.EVENTUAL);
			
			imp_source_categories_gvi = mapper.load(GlobalvarItem.class, "imp_source_categories", dynamo_config); // would otherwise be polled on every new impression
			imp_targets_gvi = mapper.load(GlobalvarItem.class, "imp_targets", dynamo_config); // would otherwise be polled on every new impression
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		super.init(servlet_config);
	}
	
	// screennames can be numbers and letters only, starting with a letter, between 3 and 15 chars long
	private boolean isValidScreenname(String incoming_string)
	{ 
		System.out.print("Testing " + incoming_string + " ");
		boolean satisfies_regex = Pattern.matches("^[a-zA-Z]([a-zA-Z0-9]){2,14}$", incoming_string);
		System.out.println(satisfies_regex);
		return satisfies_regex;
	}
	
	private boolean isValidPassword(String incoming_string)
	{
		System.out.print("Testing " + incoming_string + " ");
		boolean satisfies_regex = Pattern.matches("^(\\w|[!@\\-#$%\\*]){8,20}$", incoming_string);
		System.out.println(satisfies_regex);
		return satisfies_regex;
	}
	
	
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		//System.out.println("Dispatcher.doPost(): entering...");
		response.setContentType("application/json; charset=UTF-8;");
		response.setHeader("Access-Control-Allow-Origin","*"); //FIXME
		PrintWriter out = response.getWriter();
		//out.println("POST: Nothing to see here yet.");
		JSONObject jsonresponse = new JSONObject();
		String method = request.getParameter("method");
		long timestamp_at_entry = System.currentTimeMillis();
		try
		{
			 String screenname = request.getParameter("screenname"); // the requester's email
			 String this_access_token = request.getParameter("this_access_token"); // the requester's auth
			 if(!(screenname == null || screenname.isEmpty()) && !(this_access_token == null || this_access_token.isEmpty())) 
			 {
				// both weren't null or empty
				// if only email is null or empty respond with code "0000" to clear out the malformed credentials
				if(!(screenname == null || screenname.isEmpty()))
				{
					// otherwise, continue to user retrieval
					UserItem useritem = mapper.getUserItemFromScreenname(screenname);
					if(useritem != null)
					{	
						if(useritem.isValid(this_access_token)) 
						{	
							if (method.equals("addComment")) // email, this_access_token, target_email (of user to get) // also an admin method
							{
								 String url = request.getParameter("url"); // if this is a reply, this url value doesn't matter. The url of the parent will be used instead.
								 // this method handles all comment addition, even replies to other comments
								 // "parent" must not be null and is either the url (which will be standardized below) or the id of the parent comment
								 double silence_threshold = -5.0;
								 GlobalvarItem silence_threshold_gvi = mapper.load(GlobalvarItem.class, "silence_threshold", dynamo_config);
								 if(silence_threshold_gvi != null)
									 silence_threshold = silence_threshold_gvi.getNumberValue();
								 if(useritem.getRatingInWindow() <= silence_threshold)
								 {	 
									 jsonresponse.put("response_status", "error");
									 jsonresponse.put("message", "Your rating is too low to create comments.");
								 }
								 else
								 {	 
									// this throttling mechanism chokes the user off over time logrithmically like so: 
									 // 8/15min, 12/30min, 14/45min, 15/60min 
									 // If the user is above the threshold in any of these tests, they get choked off. 
									 // This mechanism allows for small bursts (where the user might be having a back-and-forth),
									 // but if that happens, they're only allowed 4 more in the next 15 minutes, 2 in the following 15 and one more in the last 15.
									 String text = request.getParameter("text");
									 if(text != null && text.length() > 500)
									 {
										 jsonresponse.put("response_status", "error");
										 jsonresponse.put("message", "Comments must be 500 characters or less."); 
									 }
									 else if(text == null)
									 {
										 jsonresponse.put("response_status", "error");
										 jsonresponse.put("message", "text param was null"); 
									 }
									 else if(text.isEmpty())
									 {
										 jsonresponse.put("response_status", "error");
										 jsonresponse.put("message", "text param was empty"); 
									 }
									 else
									 {	 
										 System.out.println("text=" + text);
										 TreeSet<CommentItem> comments = useritem.getCommentsAuthored(60, mapper, dynamo_config);
										 boolean throttle = false;
										 
										 if(comments != null && !useritem.getPermissionLevel().equals("admin")) // if the user has no comments, skip this. Obviously they don't need to be throttled.
										 {	 
											 TreeSet<Long> ordered_comment_longs = new TreeSet<Long>();
											 Iterator<CommentItem> ci_it = comments.iterator();
											 while(ci_it.hasNext())
												 ordered_comment_longs.add(ci_it.next().getMSFE());
											 Iterator<Long> d_it = ordered_comment_longs.descendingIterator();
											 int limit15 = 8;
											 int limit30 = 12;
											 int limit45 = 14;
											 int limit60 = 15;
											 long current_ts = 0L;
											 Calendar windowcal = Calendar.getInstance();
											 windowcal.add(Calendar.MINUTE, -15);
											 long fifteenago = windowcal.getTimeInMillis();
											 windowcal.add(Calendar.MINUTE, -15);
											 long thirtyago = windowcal.getTimeInMillis();
											 windowcal.add(Calendar.MINUTE, -15);
											 long fourtyfiveago = windowcal.getTimeInMillis();
											 windowcal.add(Calendar.MINUTE, -15);
											 long sixtyago = windowcal.getTimeInMillis();
											 int numin15 = 0;
											 int numin30 = 0;
											 int numin45 = 0;
											 int numin60 = 0;
											 while(d_it.hasNext() && numin15 < limit15 && numin30 < limit30 && numin45 < limit45 && numin60 < limit60)
											 {
												 current_ts = d_it.next();
												 if(current_ts > fifteenago)
												 	 numin15++;
												 if(current_ts > thirtyago)
													 numin30++;
												 if(current_ts > fourtyfiveago)
													 numin45++;
												 if(current_ts > sixtyago)
													 numin60++;
												 if(current_ts <= sixtyago) // as soon as we find one older than 60, break because they're ordered.
													 break;
											 }
											 if(numin15 >= limit15 || numin30 >= limit30 || numin45 >= limit45 || numin60 >= limit60)
											 {
												 throttle = true;
											 }
										 }
										
										 if(throttle)
										 {
											 System.err.println("TODO: USER=" + useritem.getScreenname() + " IS TRYING TO WRITE TOO MANY COMMENTS TOO QUICKLY.");
											 jsonresponse.put("response_status", "error");
											 jsonresponse.put("message", "Rate limit surpassed. You are writing comments too quickly. Please curb your enthusiasm.");
										 }
										 else
										 {	 
											 String parent = request.getParameter("parent"); // url or comment id
											 if(parent == null || parent.isEmpty())
											 {
												 jsonresponse.put("response_status", "error");
												 jsonresponse.put("message", "parent value required. Use the url for toplevel comments.");
											 }
											 else
											 {
												 // PASS THIS OFF TO WC for comment creation, url creation, thread creation and hostname creation.
												 if(parent.indexOf(".") != -1)
												 	 parent = null;
												 WordsCore wc = new WordsCore(mapper, dynamo_config, client);
												 jsonresponse = wc.createComment(useritem, text, url, request.getRemoteAddr(), parent); 
											 }
										 }
									 }
								 }
							}
						}
						 else // user had an email and this_access_token, but they were not valid. Let the frontend know to get rid of them
						 {
							 jsonresponse.put("response_status", "error");
							 jsonresponse.put("message", "Invalid credentials. Please try again.");
							 jsonresponse.put("error_code", "0000");
						 }
					 }
					 else // couldn't get useritem from provided email
					 {
						 jsonresponse.put("response_status", "error");
						 jsonresponse.put("message", "Invalid credentials. Please try again.");
						 jsonresponse.put("error_code", "0000");
					 }
			 	}
			 	else // either email or tat was null, but not both
			 	{
			 		jsonresponse.put("response_status", "error");
			 		jsonresponse.put("message", "Invalid credentials. Please try again.");
			 		jsonresponse.put("error_code", "0000");
			 	}
			 }	
			 else // email and tat were both null
			 {
				 jsonresponse.put("response_status", "error");
				 jsonresponse.put("message", "You must be logged in to do that.");
			 }
			 long timestamp_at_exit = System.currentTimeMillis();
			 long elapsed = timestamp_at_exit - timestamp_at_entry;
			 jsonresponse.put("elapsed", elapsed);
			 if(method != null)
				 jsonresponse.put("method", method);
			 if(devel == true)
				 System.out.println("response=" + jsonresponse);	// respond with object, success response, or error 
			 out.println(jsonresponse);
		}
		catch(JSONException jsone)
		{
			out.println("{ \"response_status\": \"error\", \"message\": \"JSONException caught in Endpoint POST\"}");
			System.err.println("endpoint: JSONException thrown in large try block. " + jsone.getMessage());
		}		
		return; 	
	}

	// error codes:
	// 0000 = delete everything
	// 0001 = delete social token
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		//System.out.println("endpoint.doGet(): entering...");
		response.setContentType("application/json; charset=UTF-8;");
		response.setHeader("Access-Control-Allow-Origin","*"); //FIXME
		PrintWriter out = response.getWriter();
		JSONObject jsonresponse = new JSONObject();
		long timestamp_at_entry = System.currentTimeMillis();
		
		try 
		{
			String method = request.getParameter("method");
			if(!request.isSecure() && !(devel == true && request.getRemoteAddr().equals("127.0.0.1")))
			{
				jsonresponse.put("message", "The w.ords.co API endpoint must be communicated with securely.");
				jsonresponse.put("response_status", "error");
			}
			else if(method == null)
			{
				jsonresponse.put("message", "Method not specified. This should probably produce HTML output reference information at some point.");
				jsonresponse.put("response_status", "error");
			}
			else
			{
				/***
				 *     _   _ _____ _   _         ___  _   _ _____ _   _  ___  ___ _____ _____ _   _ ___________  _____ 
				 *    | \ | |  _  | \ | |       / _ \| | | |_   _| | | | |  \/  ||  ___|_   _| | | |  _  |  _  \/  ___|
				 *    |  \| | | | |  \| |______/ /_\ \ | | | | | | |_| | | .  . || |__   | | | |_| | | | | | | |\ `--. 
				 *    | . ` | | | | . ` |______|  _  | | | | | | |  _  | | |\/| ||  __|  | | |  _  | | | | | | | `--. \
				 *    | |\  \ \_/ / |\  |      | | | | |_| | | | | | | | | |  | || |___  | | | | | \ \_/ / |/ / /\__/ /
				 *    \_| \_/\___/\_| \_/      \_| |_/\___/  \_/ \_| |_/ \_|  |_/\____/  \_/ \_| |_/\___/|___/  \____/ 
				 */
				if(method.equals("searchForHNItem"))
				{
					 String url_str = request.getParameter("url");
					 String result = Jsoup
							 .connect("https://hn.algolia.com/api/v1/search").data("query", url_str).data("tags","story")
							 .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.104 Safari/537.36")
							 .ignoreContentType(true).execute().body();
					 jsonresponse.put("response_status", "success");
					 jsonresponse.put("result", new JSONObject(result));
				}
				else if(method.equals("getHNAuthToken"))
				{
					String screenname = request.getParameter("screenname");
					if(screenname == null || screenname.isEmpty())
					{
						jsonresponse.put("message", "Screenname was null or empty.");
						jsonresponse.put("response_status", "error");
					}
					else
					{
						UserTokenItem uti = new UserTokenItem();
						uti.setScreenname(screenname);
						String uuid = UUID.randomUUID().toString().replaceAll("-","");
						uti.setToken(uuid);
						mapper.save(uti);
						jsonresponse.put("response_status", "success");
						jsonresponse.put("token", uuid);
					}
				}
				else if(method.equals("verifyHNUser"))
				{
					String screenname = request.getParameter("screenname");
					if(screenname == null || screenname.isEmpty())
					{
						jsonresponse.put("message", "Screenname was null or empty.");
						jsonresponse.put("response_status", "error");
					}
					else
					{
						UserTokenItem uti = mapper.load(UserTokenItem.class, screenname, dynamo_config);
						String stored_uuid = uti.getToken();
						
						int x = 0;
						String result = "";
						String about = "";
						String checked_uuid = "";
						int bi = 0;
						int ei = 0;
						int limit = 6;
						String hn_karma_str = "0";
						String hn_since_str = "0";
						//int hn_karma = 0;
						JSONObject hn_user_jo = null;
						while(x < limit)
						{
							result = Jsoup
									 .connect("https://hacker-news.firebaseio.com/v0/user/" + screenname  + ".json")
									 .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.104 Safari/537.36")
									 .ignoreContentType(true).execute().body();
							System.out.println("Endpoint.verifyHNUser():" + result);
							hn_user_jo = new JSONObject(result);
							about = hn_user_jo.getString("about");
							bi = about.indexOf("BEGINTOKEN|");
							if(bi != -1)                                   // entering here means the loop WILL break 1 of 3 ways: No |ENDTOKEN, match or no match.
							{
								ei = about.indexOf("|ENDTOKEN");
								if(ei == -1)
								{
									jsonresponse.put("response_status", "error");
									jsonresponse.put("message", "Found \"BEGINTOKEN|\" but not \"|ENDTOKEN\"");
									break;
								}
								else
								{
									checked_uuid = about.substring(bi + 11, ei);
									if(checked_uuid.equals(stored_uuid))
									{	
										String uuid_str = UUID.randomUUID().toString().replaceAll("-","");
										Calendar cal = Calendar.getInstance();
										long now = cal.getTimeInMillis();
										cal.add(Calendar.YEAR, 1);
										long future = cal.getTimeInMillis();
										UserItem useritem = mapper.getUserItemFromScreenname(screenname);
										useritem.setThisAccessToken(uuid_str);
										useritem.setThisAccessTokenExpires(future);
										useritem.setLastLoginType("hn");
										if(hn_user_jo.has("karma")) 
										{	
											hn_karma_str = hn_user_jo.getString("karma");
											if(Global.isWholeNumeric(hn_karma_str))
												useritem.setHNKarma(Integer.parseInt(hn_karma_str));
											else
												useritem.setHNKarma(0); // if "karma" is somehow not a whole integer, set to 0
										}
										else
											useritem.setHNKarma(0); // if "karma" is somehow missing, set to 0
										if(hn_user_jo.has("created")) 
										{	
											hn_since_str = hn_user_jo.getString("created");
											if(Global.isWholeNumeric(hn_since_str))
												useritem.setHNSince(Integer.parseInt(hn_since_str));
											else
												useritem.setHNSince(0); // if "karma" is somehow not a whole integer, set to 0
										}
										else
											useritem.setHNSince(0); // if "karma" is somehow missing, set to 0
										useritem.setSeen(now);
										SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
										sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
										useritem.setSeenHumanReadable(sdf.format(cal.getTimeInMillis()));
										mapper.save(useritem);
										
										//System.out.println("Endpoint.loginWithGoogleOrShowRegistration() user already registered, logging in");
										jsonresponse.put("response_status", "success");
										
										jsonresponse.put("verified", true);
										jsonresponse.put("this_access_token", uuid_str);
										jsonresponse.put("screenname", useritem.getScreenname());
										break;
									}
									else
									{
										jsonresponse.put("response_status", "error");
										jsonresponse.put("message", "Found \"BEGINTOKEN|\" and \"|ENDTOKEN\" but they did not match.");
										break;
									}
								}
							}
							else
							{
								try {
									java.lang.Thread.sleep(5000);
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								x++;
							}
						}
						if(x == limit)
						{
							jsonresponse.put("response_status", "error");
							jsonresponse.put("message", "Checked " + limit + " times and didn't find \"BEGINTOKEN|\"");
						}
						mapper.delete(uti);
					}
				}
				else if (method.equals("getMetricData")) // this allows admin to validate with FB secret for viewing metrics
				{
					GlobalvarItem metrics_msfe_gvi = mapper.load(GlobalvarItem.class, "metrics_msfe", dynamo_config);
					GlobalvarItem metrics_ttl_secs_gvi = mapper.load(GlobalvarItem.class, "metrics_ttl_secs", dynamo_config); // does not change programmatically
					GlobalvarItem metrics_gvi =  mapper.load(GlobalvarItem.class, "metrics", dynamo_config);
					long metrics_msfe = metrics_msfe_gvi.getNumberValue(); 
					long metrics_ttl_secs = metrics_ttl_secs_gvi.getNumberValue(); 
					
					 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
					 sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
					
					if(timestamp_at_entry - metrics_msfe > (metrics_ttl_secs * 1000))
					{
						// System.out.println("TTL EXPIRED");
						 metrics_msfe_gvi.setNumberValue(timestamp_at_entry); // save the new timestamp before doing anything so it doesn't repeatedly fire
						 mapper.save(metrics_msfe_gvi);
						 
						 WordsCore wc = new WordsCore(mapper, dynamo_config, client);
						 int days = 15;
						/* String days_str = request.getParameter("days");
						 if(days_str != null && Global.isWholeNumeric(days_str))
							 days = Integer.parseInt(days_str);*/
						 jsonresponse = wc.getMetricData(days);
						
						 if(!jsonresponse.has("response_status"))
						 {
							 jsonresponse = new JSONObject();
							 jsonresponse.put("message", "Unknown error getting metric data");
							 jsonresponse.put("response_status", "error");
						 }	
						 
						 jsonresponse.put("timestamp_hr", sdf.format(timestamp_at_entry)); 
						 metrics_gvi.setStringValue(jsonresponse.toString()); // save the calculated object
						 mapper.save(metrics_gvi);
						
						 jsonresponse.put("from_cache", false);
					}
					else
					{
						// System.out.println("TTL UNEXPIRED");
						 jsonresponse = new JSONObject(metrics_gvi.getStringValue());
						
						 jsonresponse.put("from_cache", true);
						// jsonresponse.put("timestamp_hr", sdf.format(metrics_msfe)); // timestamp_hr should be there from the caching... no need to add here
					}
					//System.out.println("Endpoint.getMetricData(): end");
				}
				else if(method.equals("getProgressToNextGiveaway"))
				{
					GlobalvarItem num_eligible_users_gvi = mapper.load(GlobalvarItem.class, "num_eligible_users", dynamo_config);
					int num_eligible_users = (int) num_eligible_users_gvi.getNumberValue(); 
					int target = 1000;
					if(num_eligible_users < 1000)
						target = 1000;
					else if(num_eligible_users < 2000)
						target = 2000;
					else if(num_eligible_users < 4000)
						target = 4000;
					else if(num_eligible_users < 8000)
						target = 8000;
					else if(num_eligible_users < 16000)
						target = 16000;
					else if(num_eligible_users < 32000)
						target = 32000;
					else if(num_eligible_users < 64000)
						target = 64000;
					else if(num_eligible_users < 128000)
						target = 128000;
					else
					{
						target = 10000000; // ten million after 128000 has been hit
					}
					jsonresponse.put("response_status", "success");
					jsonresponse.put("num_eligible_users", num_eligible_users);
					jsonresponse.put("target", target);
				}
				else if(method.equals("nativeLogin"))
				{
					String screenname = request.getParameter("screenname");
					if(screenname == null || screenname.isEmpty())
					{
						jsonresponse.put("message", "Screenname was null or empty.");
						jsonresponse.put("response_status", "error");
					}
					else
					{
						String password = request.getParameter("password");
						UserItem useritem = mapper.getUserItemFromScreenname(screenname);
						if(password == null || password.isEmpty())
						{
							jsonresponse.put("message", "Password was null or empty.");
							jsonresponse.put("response_status", "error");
						}
						else if(useritem == null)
						{
							jsonresponse.put("message", "Screenname/password combination is invalid.");
							jsonresponse.put("response_status", "error");
						}
						else 
						{
							if(useritem.isCurrentPassword(password))
							{
								 String uuid_str = UUID.randomUUID().toString().replaceAll("-","");
								 Calendar cal = Calendar.getInstance();
								 long now = cal.getTimeInMillis();
								 cal.add(Calendar.YEAR, 1);
								 long future = cal.getTimeInMillis();
								 
								 useritem.setThisAccessToken(uuid_str);
								 useritem.setThisAccessTokenExpires(future);
								 useritem.setLastLoginType("words");
								 useritem.setSeen(now);
								 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
								 sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
								 useritem.setSeenHumanReadable(sdf.format(cal.getTimeInMillis()));
								 mapper.save(useritem);
								 
								 //System.out.println("Endpoint.loginWithGoogleOrShowRegistration() user already registered, logging in");
								 jsonresponse.put("response_status", "success");
								 jsonresponse.put("this_access_token", uuid_str);
								 jsonresponse.put("screenname", useritem.getScreenname());
								 jsonresponse.put("show_registration", "false");
								 jsonresponse.put("login_type", "words");
							}
							else
							{
								jsonresponse.put("message", "That screenname/password combination is invalid.");
								jsonresponse.put("response_status", "error");
							}
						}
					}
				}
				else if(method.equals("sendPasswordResetEmail"))
				{
					String email = request.getParameter("email");
					if(email == null)
					{
						jsonresponse.put("response_status", "error");
						jsonresponse.put("message", "Email address parameter required.");
					}
					else
					{
						EmailValidator ev = EmailValidator.getInstance(false); // local addresses (@localhost, eg) should not be allowed.
						if(!ev.isValid(email))
						{
							jsonresponse.put("response_status", "error");
							jsonresponse.put("message", "That email address is not properly formed.");
						}
						else
						{	
							UserItem useritem = mapper.getUserItemFromEmail(email);
							if(useritem == null)
							{
								jsonresponse.put("response_status", "success"); // always return success here so a potential attacker doesn't know if the email address exists in the db
							}
							else
							{
								useritem.sendPasswordResetEmail(mapper, dynamo_config);
								jsonresponse.put("response_status", "success");
							}
						}
					}
				}
				else if(method.equals("confirmPasswordReset"))
				{
					String email = request.getParameter("email");
					String confcode = request.getParameter("confcode");
					if(email == null)
					{
						jsonresponse.put("response_status", "error");
						jsonresponse.put("message", "Email address parameter required.");
					}
					else if(confcode == null)
					{
						jsonresponse.put("response_status", "error");
						jsonresponse.put("message", "confcode parameter required.");
					}
					else
					{
						UserItem useritem = mapper.getUserItemFromEmail(email);
						long twenty_minutes_ago = System.currentTimeMillis() - 1200000;
						if(confcode.equals(useritem.getPasswordResetConfirmationCode()) && useritem.getPasswordResetConfirmationCodeMSFE() >= twenty_minutes_ago)
						{
							
							String password = UUID.randomUUID().toString().replaceAll("-","").substring(0,8);
							SHARight sr = new SHARight();
							String salt = null;
							try {
								salt = sr.getSalt();
								String secpass = sr.get_SHA_512_SecurePassword(password, salt);
								useritem.setEncryptedPassword(secpass);
								useritem.setSalt(salt);
								mapper.save(useritem);
								useritem.emailNewlyResetPassword(password);
								jsonresponse.put("response_status", "success"); // always return success here so a potential attacker doesn't know if the email address exists in the db
							} catch (NoSuchAlgorithmException e) {
								jsonresponse.put("response_status", "error");
								jsonresponse.put("message", "No such algorithm exception when trying to reset password.");
								e.printStackTrace();
							}
							
						}
						else if(!confcode.equals(useritem.getPasswordResetConfirmationCode()))
						{
							jsonresponse.put("response_status", "error");
							jsonresponse.put("message", "Incorrect confirmation code.");
						}
						else if(useritem.getPasswordResetConfirmationCodeMSFE() < twenty_minutes_ago)
						{
							jsonresponse.put("response_status", "error");
							jsonresponse.put("message", "Confirmation code has expired.");
						}
						else
						{
							jsonresponse.put("response_status", "error");
							jsonresponse.put("message", "Unknown error");
						}	
					}
				}
				else if(method.equals("getMostRecentComments"))
				{
					long now = System.currentTimeMillis();
					GlobalvarItem mrc_msfe_gvi = mapper.load(GlobalvarItem.class, "mrc_msfe", dynamo_config);
					GlobalvarItem mrc_ttl_secs_gvi = mapper.load(GlobalvarItem.class, "mrc_ttl_secs", dynamo_config); // does not change programmatically
					GlobalvarItem mrc_gvi =  mapper.load(GlobalvarItem.class, "mrc", dynamo_config);
					long mrc_msfe = mrc_msfe_gvi.getNumberValue(); 
					long mrc_ttl_secs = mrc_ttl_secs_gvi.getNumberValue(); 
					//System.out.println("               now=" + now);
					//System.out.println("     mrc_msfe=" + mrc_msfe);
					//System.out.println(" mrc_ttl_secs=" + mrc_ttl_secs);
					//System.out.println(" now-mrc_msfe=" + (now - mrc_msfe));
					//System.out.println("mrc_ttl*1000=" + (mrc_ttl_secs * 1000));
					if(now - mrc_msfe > (mrc_ttl_secs * 1000))
					{
						// System.out.println("TTL EXPIRED");
						 mrc_msfe_gvi.setNumberValue(now); // update the new timestamp first before doing anything
						 mapper.save(mrc_msfe_gvi);
						 
						 DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
						 List<CommentItem> scanResult = mapper.scan(CommentItem.class, scanExpression);
						 TreeSet<CommentItem> ts = new TreeSet<CommentItem>();
						 for (CommentItem ci : scanResult) {
							 ts.add(ci);
						 }
						 TreeSet<String> returnset = new TreeSet<String>();
						 Iterator<CommentItem> it = ts.iterator();
						 int x = 0;
						 CommentItem current_ci = new CommentItem();
						 while(it.hasNext() && x < 25) // always get the most recent 25 items. We can't do a stored TTL if the number to return differs
						 {
							 current_ci = it.next();
							 System.out.println(current_ci.getMSFE() + " " + current_ci.getHPQSP() + " " + current_ci.getText());
							 returnset.add(current_ci.getId());
							 x++;
						 }
						
						 mrc_gvi.setStringSetValue(returnset); // save the calculated object
						 mapper.save(mrc_gvi);
						 
						 //System.out.println("adding mrc=" + new JSONArray(returnset));
						 jsonresponse.put("mrc", new JSONArray(returnset));
					 }
					 else
					 {
						 //System.out.println("TTL unexpired");
						// System.out.println("adding mrc=" + mrc_gvi.getStringSetValue());
						 jsonresponse.put("mrc", mrc_gvi.getStringSetValue());
					 }
					jsonresponse.put("response_status", "success");
				}
				else if(method.equals("getAccessTokenFromAuthorizationCode"))
				{
					//System.out.println("Endpoint.getAccessTokenFromAuthorizationCode(): begin");
					String code = request.getParameter("code");
					String login_type = request.getParameter("login_type");
					if(code == null || code.isEmpty())
					{
						jsonresponse.put("message", "This method requires a code value.");
						jsonresponse.put("response_status", "error");
					}
					else if(login_type == null || code.isEmpty())
					{
						jsonresponse.put("message", "This method requires a login_type value");
						jsonresponse.put("response_status", "error");
					}
					else
					{	
						if(login_type.equals("google"))
						{
							//System.out.println("Endpoint.getAccessTokenFromAuthorizationCode(google):");
							// We want to receive this authorization code, get the access_token, and return the user's full profile all at once
							String google_client_id = request.getParameter("client_id");
							String google_client_secret = null;
							String redirect_uri = request.getParameter("redirect_uri");
							//String redirect_uri = "urn:ietf:wg:oauth:2.0:oob"; 
							String grant_type = "authorization_code";

							BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("GoogleCredentials.properties")));
							String currentline = "";
							try {
								while((currentline = br.readLine()) != null)
								{
									//if(currentline.indexOf("client_id=") != -1)
									//	google_client_id = currentline.substring(10);
									if(google_client_id.equals("591907226969-fgjpelovki35oc3nclvu512msaka2hfh.apps.googleusercontent.com"))
									{	
										if(currentline.indexOf("devcli_secret=") != -1)
											google_client_secret = currentline.substring(14);
									}
									else
									{
										if(currentline.indexOf("client_secret=") != -1)
											google_client_secret = currentline.substring(14);
									}
								}
								JSONObject code_for_token_response_jo = null;
								 HttpClient httpclient = new DefaultHttpClient();
								 try {
									 List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(5);
									 System.out.println("Adding \"code\"=" + code);
									 nameValuePairs.add(new BasicNameValuePair("code", code));
									 System.out.println("Adding \"client_id\"=" + google_client_id);
									 nameValuePairs.add(new BasicNameValuePair("client_id", google_client_id));
									 System.out.println("Adding \"client_secret\"=" + google_client_secret);
									 nameValuePairs.add(new BasicNameValuePair("client_secret", google_client_secret));
									 System.out.println("Adding \"redirect_uri\"=" + redirect_uri); 
									 nameValuePairs.add(new BasicNameValuePair("redirect_uri", redirect_uri));
									 System.out.println("Adding \"grant_type\"=" + grant_type);
									 nameValuePairs.add(new BasicNameValuePair("grant_type", grant_type));
									 HttpPost httppost = new HttpPost("https://accounts.google.com/o/oauth2/token");
									 httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs, "utf-8"));
									 ResponseHandler<String> responseHandler = new BasicResponseHandler();
									 String responseBody = httpclient.execute(httppost, responseHandler);
									 System.out.println("response from https://accounts.google.com/o/oauth2/token=" + responseBody);
									 code_for_token_response_jo = new JSONObject(responseBody);
									 if(code_for_token_response_jo.has("error") || !code_for_token_response_jo.has("access_token") || !code_for_token_response_jo.has("expires_in"))
									 {
										 jsonresponse.put("response_status", "error");
										 jsonresponse.put("message", " Error from google using authorization_code to get access_token.");
										 jsonresponse.put("error_code", "0000");
										 jsonresponse.put("login_type", "google");
									 }
									 else // this is the success condition
									 {
										 jsonresponse = loginWithGoogleOrShowRegistration(code_for_token_response_jo.getString("access_token"));
									 }	
							     } 
								 catch(ClientProtocolException cpe)  
								 {	
									 System.err.println("Endpoint.getAccessTokenFromAuthorizationCode(): ClientProtocolException: " + cpe.getMessage());
									 // if we've gotten here, there's a near-certain chance the user's token is no longer valid. Need to return a "delete cookies" signal.
									 jsonresponse.put("response_status", "error");
									 jsonresponse.put("message", "Your Google token is no longer valid. Please log in again.");
									 jsonresponse.put("error_code", "0000");
									 jsonresponse.put("login_type", "google");
								 }
								 finally { httpclient.getConnectionManager().shutdown(); } 

							} catch (IOException e) {
								System.err.println("IOException attempting to read Google credentials file.");
								e.printStackTrace();
								jsonresponse.put("message", "There was an error attempting to log you in with Google. Please try another login method or come back later. Sorry!");
								jsonresponse.put("response_status", "error");
								jsonresponse.put("login_type", "google");
							}
						}
						else if(login_type.equals("facebook"))
						{
							//System.out.println("Endpoint.getAccessTokenFromAuthorizationCode(facebook):");
							BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("FacebookCredentials.properties")));
							String currentline = "";
							String facebook_app_id = "";
							String facebook_app_secret = "";
							String redirect_uri = request.getParameter("redirect_uri");
							try {
								while((currentline = br.readLine()) != null)
								{
									if(currentline.indexOf("app_id=") != -1)
										facebook_app_id = currentline.substring(7);
									else if(currentline.indexOf("app_secret=") != -1)
										facebook_app_secret = currentline.substring(11);
								}
								HttpClient client = new DefaultHttpClient();
								try {
									HttpGet httprequest = new HttpGet("https://graph.facebook.com/oauth/access_token?" +
											"client_id=" + facebook_app_id + 
											"&client_secret=" + facebook_app_secret + 
											"&code=" + code +
											//"&redirect_uri=https://www.facebook.com/connect/login_success.html" +  
											"&redirect_uri=" + redirect_uri
											);
									
									HttpResponse httpresponse = client.execute(httprequest);
									BufferedReader rd = new BufferedReader(new InputStreamReader(httpresponse.getEntity().getContent()));
									String text = ""; String currenttoken = ""; String access_token = ""; String expires_str = ""; long expires_long = 0L;
									while ((currentline = rd.readLine()) != null) {
										text = text + currentline;
									} 
									//System.out.println("Code-for-Access-token raw response from FB=" + text);
									if(text.indexOf("access_token=") == -1) 
									{
										JSONObject jo = new JSONObject(text);
										if(jo.has("error"))
										{
											System.err.println("The code-for-token request to facebook failed with an error.");
											if(jo.getJSONObject("error").has("message"))
											{
												System.err.println("The code-for-token request to facebook failed with an error. message=" + jo.getJSONObject("error").getString("message"));
											}
											if(jo.getJSONObject("error").has("type"))
											{
												System.err.println("The code-for-token request to facebook failed with an error. type=" + jo.getJSONObject("error").getString("type"));
											}
											if(jo.getJSONObject("error").has("code"))
											{
												System.err.println("The code-for-token request to facebook failed with an error. code=" + jo.getJSONObject("error").getString("code"));
											}
										}
										jsonresponse.put("response_status", "error");
										jsonresponse.put("message", "The authorization request to FB failed. If repeatedly see this message, please go to FB, settings, apps and remove the WORDS app before trying again.");
										jsonresponse.put("error_code", "0000");
										jsonresponse.put("login_type", "facebook");
									}
									else if(text.indexOf("access_token=") != -1 && text.indexOf("expires=") != -1) // successful response
									{	
										StringTokenizer st = new StringTokenizer(text,"&");
										while(st.hasMoreTokens())
										{
											currenttoken = st.nextToken();
											if(currenttoken.startsWith("access_token="))
												access_token = currenttoken.substring(currenttoken.indexOf("=") + 1);
											if(currenttoken.startsWith("expires="))
											{
												expires_str = currenttoken.substring(currenttoken.indexOf("=") + 1);
												//System.out.println("expires_str=" + expires_str);
												expires_long = (Long.parseLong(expires_str)*1000) + timestamp_at_entry; // convert seconds to milliseconds and add to timestamp at entry
																														// as of this note, FB tokens last 60 days		
												//System.out.println("expires_long=" + expires_long);
											}
											
										}
										jsonresponse = loginWithFacebookOrShowRegistration(access_token); 
									}
								}
								catch(ClientProtocolException cpe)  
								{	
									System.err.println("Endpoint.getAccessTokenFromAuthorizationCode(): ClientProtocolException: " + cpe.getMessage());
									System.err.println("ClientProtocolException: " + cpe.getMessage() + " " + cpe.getCause() + " " + cpe.getLocalizedMessage()); 
									 cpe.printStackTrace();
									// if we've gotten here, there's a near-certain chance the user's token is no longer valid. Need to return a "delete cookies" signal.
									jsonresponse.put("response_status", "error");
									jsonresponse.put("message", "Your Facebook token is no longer valid. Please log in again.");
									jsonresponse.put("error_code", "0000");
									jsonresponse.put("login_type", "facebook");
								}
							}
							catch (IOException e) 
							{
								System.err.println("Endpoint.getAccessTokenFromAuthorizationCode IOException attempting to read Facebook credentials file.");
								e.printStackTrace();
								jsonresponse.put("message", "There was an error attempting to log you in with Facebook. Please try another login method or come back later. Sorry!");
								jsonresponse.put("response_status", "error");
								jsonresponse.put("login_type", "facebook");
							}
						}
						else
						{
							jsonresponse.put("message", "Unknown login_type value.");
							jsonresponse.put("response_status", "error");
						}
					}
					//System.out.println("Endpoint.getAccessTokenFromAuthorizationCode(): end");
				 }
				 else if (method.equals("getThread"))
				 {			
						/*
						 *  {
	    "elapsed": 182,
	    "msfe": 1394418255902,
	    "thread_jo": {
	        "original_url": "http://www.drudgereport.com/",
	        "combined_or_separated": "combined",
	        "hostname": "www.drudgereport.com",
	        "children": ["OY452PLnBTC", "OY455aTY48C", "OY456LOtSeC", "OY495WXCBBC", "OY4IsMjFb1C"],
	        "significant_designation": "www.drudgereport.com"
	    },
	    "response_status": "success"
							}
		        	 */
					 //System.out.println("Endpoint.getThread(): begin");
					 String url = request.getParameter("url");
					 if(url != null && url.indexOf("#") != -1)
						 url = url.substring(0, url.indexOf("#"));
					 if(url != null && (url.indexOf("/", url.indexOf("://") + 3) == -1)) // if of the form http://www.yahoo.com, make it http://www.yahoo.com/ 
							url = url + "/";
					 boolean valid_url = Global.isValidURLFormation(url, true); // require proto_colon_slashslash
					 if(!valid_url)
					 {
						 // invalid url, don't get thread
						 jsonresponse.put("message", "Invalid url.");
						 jsonresponse.put("response_status", "error");
					 }
					 else
					 {
						 co.ords.w.Thread t = new co.ords.w.Thread(url, mapper, dynamo_config);
						 if(!t.isValid())
						 {
							 jsonresponse.put("response_status", "error");
							 jsonresponse.put("message", "url parameter was invalid");
						 }
						 else
						 {
							 boolean getpagetitle = false;
							 JSONObject thread_jo = null;
							 thread_jo = t.getJSONObjectRepresentation(getpagetitle, client);
							 
							 jsonresponse.put("response_status", "success");
							 jsonresponse.put("thread_jo", thread_jo);

							 // I thought about making this asynchronous, but then I wondered if this simple increment
							 // might actually be faster than setting up and spinning off another thread
							 // either way, probably negligible
							 // note: this should probably be an atomic increment. Not sure how additionally costly this method is, but prob negligible unless at extreme scale.
							 long templong = 0L;
							 GlobalvarItem thread_retrievals_gvi = mapper.load(GlobalvarItem.class, "thread_retrievals", dynamo_config); // keep this eventual?
							 if(thread_retrievals_gvi != null)
							 {
								 templong = thread_retrievals_gvi.getNumberValue() + 1;
								 thread_retrievals_gvi.setNumberValue(templong);
								 mapper.save(thread_retrievals_gvi);
							 }
							 else // if it doesn't exist for some crazy reason, just create it at 0
							 {
								// do not attempt to create it. thread_retrievals_gvi might be null because of network or something, don't want to reset to 0
							 }
						 }
					 }
					 // this is included on all getThread calls to ensure that frontend has the proper time
					 long now = System.currentTimeMillis();
					 jsonresponse.put("msfe", now);
					 
					 GlobalvarItem comcount_msfe_gvi = mapper.load(GlobalvarItem.class, "comcount_msfe", dynamo_config);
					 GlobalvarItem comcount_ttl_mins_gvi = mapper.load(GlobalvarItem.class, "comcount_ttl_mins", dynamo_config); // does not change programmatically
					 GlobalvarItem comcount_gvi =  mapper.load(GlobalvarItem.class, "comcount", dynamo_config);
					 long comcount_msfe = comcount_msfe_gvi.getNumberValue(); 
					 long comcount_ttl_mins = comcount_ttl_mins_gvi.getNumberValue(); 
					 //System.out.println("               now=" + now);
					// System.out.println("     comcount_msfe=" + comcount_msfe);
					// System.out.println(" comcount_ttl_mins=" + comcount_ttl_mins);
					// System.out.println(" now-comcount_msfe=" + (now - comcount_msfe));
					// System.out.println("comcount_ttl*60000=" + (comcount_ttl_mins * 60000));
					 if(now - comcount_msfe > (comcount_ttl_mins * 60000))
					 {
						 //System.out.println("TTL EXPIRED");
						 long comcount_window_mins = mapper.load(GlobalvarItem.class, "comcount_window_mins", dynamo_config).getNumberValue(); // does not change programmatically
						 DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
						 List<CommentItem> scanResult = mapper.scan(CommentItem.class, scanExpression);
						 int count = 0;
						 for (CommentItem ti : scanResult) {
							 if(ti.getMSFE() > (now - (comcount_window_mins * 60000)))
							 {
								// System.out.print("incrementing ");
								 count++;
							 }
							// System.out.println();
						 }
						 comcount_msfe_gvi.setNumberValue(now);
						 comcount_gvi.setNumberValue(count);
						 mapper.save(comcount_msfe_gvi);
						 mapper.save(comcount_gvi);
						 //System.out.println("adding comcount=" + count);
						 jsonresponse.put("comcount", count);
					 }
					 else
					 {
						// System.out.println("TTL unexpired");
						// System.out.println("adding comcount=" + comcount_gvi.getNumberValue());
						 jsonresponse.put("comcount", comcount_gvi.getNumberValue());
					 }
					// System.out.println("Endpoint.getThread(): end");
				 }		
				 else if (method.equals("getFeedItem")) 
				 {
					 //System.out.println("Endpoint.getFeedItem(): begin");
					 String id = request.getParameter("id"); // this is now commentid#childid (like, dislike or reply)
					 if(id.endsWith("L"))
					 {
						 LikeItem l = mapper.load(LikeItem.class, id, dynamo_config);
						 if(l == null)
						 {
							 jsonresponse.put("response_status", "error");
							 jsonresponse.put("message", "Couldn't find LikeItem in database");
						 }
						 else
						 {	 
							 jsonresponse.put("response_status", "success");
							 jsonresponse.put("item",l.getJSONObject(mapper, dynamo_config));
						 }
					 }
					 else if(id.endsWith("D"))
					 {
						 DislikeItem d = mapper.load(DislikeItem.class, id, dynamo_config);
						 if(d == null)
						 {
							 jsonresponse.put("response_status", "error");
							 jsonresponse.put("message", "Couldn't find DislikeItem in database");
						 }
						 else
						 {	 
							 jsonresponse.put("response_status", "success");
							 jsonresponse.put("item",d.getJSONObject(mapper, dynamo_config));
						 }
					 }
					 else if(id.endsWith("M") || id.endsWith("R") || id.endsWith("F") || id.endsWith("C"))
					 {
						 id = id.substring(0,10) + "C"; // does nothing if already ending in C, obviously
						 CommentItem c = mapper.load(CommentItem.class, id, dynamo_config);
						 if(c == null)
						 {
							 jsonresponse.put("response_status", "error");
							 jsonresponse.put("message", "Couldn't find CommentItem in database");
						 }
						 else
						 {	 
							 jsonresponse.put("response_status", "success");
							 jsonresponse.put("item",c.getAsJSONObject(mapper, dynamo_config, false)); // do not get author stuff if hidden
						 }
					 }
					 else
					 {
						 jsonresponse.put("response_status", "error");
						 jsonresponse.put("message", "Invalid feed item id structure.");
					 }
					 //System.out.println("Endpoint.getFeedItem(): end");
				 }
				 else if (method.equals("isScreennameAvailable")) 
				 {
					 //System.out.println("Endpoint.isScreennameAvailable(): begin");
					 String screenname = request.getParameter("screenname"); 
					 if(screenname == null || !isValidScreenname(screenname))
					 {
						 jsonresponse.put("response_status", "error");
						 jsonresponse.put("message", "invalid");
					 }
					 else
					 {
						 UserItem u = mapper.getUserItemFromScreenname(screenname);
						 boolean screenname_is_available =  (u == null) ? true : false;
						 if(screenname_is_available)
						 {
							 jsonresponse.put("response_status", "success");
							 jsonresponse.put("screenname_available", "true");
						 }
						 else
						 {
							 jsonresponse.put("response_status", "success");
							 jsonresponse.put("screenname_available", "false");
						 }
					 }
					 //System.out.println("Endpoint.isScreennameAvailable(): end"); 
				 }
				 else if(method.equals("noteThreadView"))
				 {
					 //System.out.println("Endpoint.noteThreadView(): begin");
					 // NOTE: if you're following this from the frontend,
					 // you'll see that user information and the current URL has arrived here. 
					 // The user info (if supplied) is used to increment the user's threadview count. Nothing more. 
					 // The URL used to get the bits of information below, but is not saved in the database.
					 // Only 5 items are recorded: 1. Was the thread empty? 2. Was the (now anonymous) user logged in at the time of threadview? 
					 // 3. Was the hostname combined or separated? 4. Did the hostname/path have an significant QSPs (like youtube) 5. Were we able to show link alternatives?
					 // This information will help us make WORDS better, cus it sucks to, for instance, show empty threads when someone clicks the button.
					 // We want to know how often that's happening.
					 
					 // but first...
					 // for now, on every threadview, check the global var to see if we need a statistics snapshot
					 // later, as traffic increases, put this in a location where it'll fire more rarely
					 
					 GlobalvarItem stats_snapshot_msfe_gvi = mapper.load(GlobalvarItem.class, "stats_snapshot_msfe", dynamo_config);
					 GlobalvarItem stats_snapshot_ttl_secs_gvi = mapper.load(GlobalvarItem.class, "stats_snapshot_ttl_secs", dynamo_config); // does not change programmatically
					 
					 if(stats_snapshot_msfe_gvi != null)
					 {	 
						 long stats_snapshot_msfe = stats_snapshot_msfe_gvi.getNumberValue(); 
						 long stats_snapshot_ttl_secs = stats_snapshot_ttl_secs_gvi.getNumberValue(); 
							
						 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
						 sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
							
						 if(timestamp_at_entry - stats_snapshot_msfe > (stats_snapshot_ttl_secs * 1000))
						 {
								 //System.out.println("Endpoint.noteThreadView(): firing snapshot");
								 // TTL has expired
								 // first, set the new timestamp so this doesn't keep firing
							 	 stats_snapshot_msfe_gvi.setNumberValue(timestamp_at_entry);
								 mapper.save(stats_snapshot_msfe_gvi);
								 // now start the snapshotting process in separate thread
								 StatsSnapshotter snap = new StatsSnapshotter(mapper, dynamo_config, client);
								 snap.start();
								 // and continue to handle the request that triggered this
						 }
					 }
					 else
					 {
						 System.err.println("Error! Couldn't get statistics_snapshot_msfe from globalvar table. No snapshots will be taken until this is resolved.");
					 }
					 
					 boolean was_logged_in = false;
					 String screenname = request.getParameter("screenname");
					 String this_access_token = request.getParameter("this_access_token");
					 UserItem useritem = null;
					 if(screenname != null && this_access_token != null && !screenname.isEmpty() && !this_access_token.isEmpty())
					 {	 
						 useritem = mapper.getUserItemFromScreenname(screenname);
						 if(useritem != null && useritem.getThisAccessToken().equals(this_access_token))
						 {
							 was_logged_in = true;
							 // increment the user's threadview count and set last active timestamp
							 long threadviews = useritem.getThreadViews();
							 threadviews++;
							 useritem.setThreadViews(threadviews);
							 useritem.setLastActiveMSFE(timestamp_at_entry);
							 
							 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
							 sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
							 
							 useritem.setLastActiveHumanReadable(sdf.format(timestamp_at_entry));
							 mapper.save(useritem);
						 }
					 }
					 useritem = null; // user information does not go any further
					 
					
					 String was_empty = request.getParameter("was_empty");
					 String url = request.getParameter("url");
					 if(was_empty == null || url == null)
					 {
						 jsonresponse.put("message", "Invalid parameters.");
						 jsonresponse.put("response_status", "error");
					 }
					 else if(!(was_empty.equals("true") || was_empty.equals("false")))
					 { 
						 jsonresponse.put("message", "Invalid parameters.");
						 jsonresponse.put("response_status", "error");
					 }
					 else
					 {
						 if(url != null && url.indexOf("#") != -1)
							 url = url.substring(0, url.indexOf("#"));
						 if(url != null && (url.indexOf("/", url.indexOf("://") + 3) == -1)) // if of the form http://www.yahoo.com, make it http://www.yahoo.com/ 
								url = url + "/";
						 String hostname = Global.getStandardizedHostnameFromURL(url);
						 String hp = Global.getStandardizedHPFromURL(url);
						 if(hp == null || hostname == null)
						 {
							 // invalid url, don't record this
							 jsonresponse.put("message", "Invalid parameters.");
							 jsonresponse.put("response_status", "error");
						 }
						 else
						 {	 
							 HostnameItem hi = mapper.load(HostnameItem.class, hostname, dynamo_config);
							 HPItem hpi = mapper.load(HPItem.class, hp, dynamo_config);
							 boolean hostname_separated = false;
							 if(hi != null && hi.getSeparated())
								 hostname_separated = true;
							 boolean was_empty_bool = false;
							 if(was_empty.equals("true"))
								 was_empty_bool = true;
							 ThreadViewItem tvi = new ThreadViewItem();
							 String uuid_str = UUID.randomUUID().toString().replaceAll("-","");
							 tvi.setId(uuid_str);
							 tvi.setMSFE(timestamp_at_entry);
							 
							 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
							 sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
							 
							 tvi.setTimestampHumanReadable(sdf.format(timestamp_at_entry));
							 tvi.setWasEmpty(was_empty_bool);
							 tvi.setWasLoggedIn(was_logged_in);
							 tvi.setHostnameWasSeparated(hostname_separated);
							 if(hostname_separated)
								 tvi.setSignificantDesignation(Global.getHPQSPAccordingToThisURL(url, mapper));
							 else
								 tvi.setSignificantDesignation(Global.getStandardizedHostnameFromURL(url));
							 boolean hp_has_sqsps = false;
							 if(hpi != null && hpi.getSignificantQSPs() != null)
								 hp_has_sqsps = true;
							 tvi.setHPHadSQSPS(hp_has_sqsps);
							 mapper.save(tvi);
							 jsonresponse.put("response_status", "success");
						 }
					 }
					 //System.out.println("Endpoint.noteThreadView(): end");
				 }
				 else if (method.equals("getPageLikes")) // hostname or hpqsp hybrid method. which=hpqsp|hostname
				 {
					 //System.out.println("Endpoint.getPageLikes() begin");
					 String url = request.getParameter("url"); 
					 if(url != null && url.indexOf("#") != -1)
						 url = url.substring(0, url.indexOf("#"));
					 if(url != null && (url.indexOf("/", url.indexOf("://") + 3) == -1)) // if of the form http://www.yahoo.com, make it http://www.yahoo.com/ 
							url = url + "/";
					 boolean valid_url = Global.isValidURLFormation(url, true); // require proto_colon_slashslash
					 if(!valid_url)
					 {
						 // invalid url, don't getPageLikes
						 jsonresponse.put("message", "Invalid url.");
						 jsonresponse.put("response_status", "error");
					 }
					 else
					 {
						 String which = request.getParameter("which");
						 if(which == null || !(which.equals("hostname") || which.equals("hpqsp")))
						 {
							 jsonresponse.put("message", "Invalid which parameter.");
							 jsonresponse.put("response_status", "error");
						 }
						 else
						 {	 
							 long count = 0L;
							 if(which.equals("hostname"))
							 {
								 HostnameItem hostnameitem = mapper.load(HostnameItem.class, Global.getStandardizedHostnameFromURL(url), dynamo_config);
								 if(hostnameitem != null)
								 {
									 TreeSet<HostnameLikeItem> throwaway = hostnameitem.getHostnameLikes(0, mapper, dynamo_config);
									 if(throwaway != null)
										 count = hostnameitem.getHostnameLikes(0, mapper, dynamo_config).size();
									 else
										 count = 0;
								 }
								 else 
									 count = 0;
							 }
							 else // if not hostname, we know it's hpqsp from the check above
							 {
								 HPQSPItem hpqspitem = mapper.load(HPQSPItem.class, Global.getHPQSPAccordingToThisURL(url, mapper), dynamo_config);
								 if(hpqspitem != null)
								 {
									 TreeSet<HPQSPLikeItem> throwaway = hpqspitem.getHPQSPLikes(0, mapper, dynamo_config);
									 if(throwaway != null)
										 count = hpqspitem.getHPQSPLikes(0, mapper, dynamo_config).size();
									 else
										 count = 0;
								 }
								 else 
									 count = 0;
							 }
							 jsonresponse.put("response_status", "success");
							 jsonresponse.put("count", count);
						 }
					 }
					 //System.out.println("Endpoint.getPageLikes() end");
				 }
				 else if(method.equals("getMostActivePages"))
				 {
					 //System.out.println("Endpoint.getMostActivePagesForThisHostname() begin");
					 
					 long num_hours = 24; // default to something sane
					 GlobalvarItem trending_activity_hours_gvi = mapper.load(GlobalvarItem.class, "trending_activity_hours", dynamo_config);
					 if(trending_activity_hours_gvi != null)
						 num_hours = trending_activity_hours_gvi.getNumberValue();
					 int num_hours_int = (new Long(num_hours)).intValue();
					 
					 String hostname = request.getParameter("hostname");
					 if(hostname != null && (hostname.isEmpty() || hostname.equals("null")))
						 hostname = null;
					 WordsCore wc = new WordsCore(mapper, dynamo_config, client);
					 JSONObject jo = wc.getMostActivePages(num_hours_int, hostname); // hostname can be null 
					 if(jo == null)
					 {
						 jsonresponse.put("message", "Couldn't get trending object.");
						 jsonresponse.put("response_status", "error");
					 }
					 else
						 jsonresponse = jo; // this isn't necessarily success. TTU.getTrending can return a jo error, too.
					 //System.out.println("Endpoint.getMostActivePagesForThisHostname() end");
				 }
				 else if(method.equals("getMostLikedPages"))
				 {
					 // System.out.println("Endpoint.getMostLikedPagesForThisHostname() begin");
					 long num_hours = 24; // default to something sane
					 GlobalvarItem trending_activity_hours_gvi = mapper.load(GlobalvarItem.class, "trending_activity_hours", dynamo_config);
					 if(trending_activity_hours_gvi != null)
						 num_hours = trending_activity_hours_gvi.getNumberValue();
					 int num_hours_int = (new Long(num_hours)).intValue(); // this really shouldn't be a problem, but does it need to be checked for validity?
					 
					 String hostname = request.getParameter("hostname");
					 if(hostname != null && (hostname.isEmpty() || hostname.equals("null")))
						 hostname = null;
					 WordsCore wc = new WordsCore(mapper, dynamo_config, client);
					 JSONObject jo = wc.getMostLikedPages(num_hours_int, hostname); // hostname can be null 
					 if(jo == null)
					 {
						 jsonresponse.put("message", "Couldn't get trending object.");
						 jsonresponse.put("response_status", "error");
					 }
					 else
						 jsonresponse = jo; // this isn't necessarily success. TTU.getTrending can return a jo error, too.
					 // System.out.println("Endpoint.getMostLikedPagesForThisHostname() end");
				 }
				 else if(method.equals("noteImpression"))
				 {
					 String source_category = request.getParameter("source_category");
					 String target = request.getParameter("target"); // separated by spaces
					 
					 if(target == null || source_category ==  null || source_category.isEmpty() || target.isEmpty())
					 {
						 jsonresponse.put("message", "Invalid parameters.");
						 jsonresponse.put("response_status", "error");
					 }
					 else
					 {
						 if(imp_source_categories_gvi != null)
						 {
							 Set<String> allowed_set = imp_source_categories_gvi.getStringSetValue();
							 if(allowed_set.contains(source_category))
							 {
								 if(imp_targets_gvi != null)
								 {
									 allowed_set = imp_targets_gvi.getStringSetValue();
									 if(allowed_set.contains(target))
									 {
										 String uuid_str = UUID.randomUUID().toString().replaceAll("-","");
										 ImpressionItem pi = new ImpressionItem();
										 pi.setId(uuid_str);
										 pi.setImpressionMSFE(timestamp_at_entry);
										 
										 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
										 sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
										 
										 pi.setTimestampHumanReadable(sdf.format(timestamp_at_entry));
										 pi.setTarget(target);
										 pi.setSourceCategory(source_category);
										 mapper.save(pi);
										 jsonresponse.put("response_status", "success");
										 jsonresponse.put("id", uuid_str);
									 }
									 else
									 {
										 System.err.println("Endpoint: noteImpression disallowed target=" + target);
										 jsonresponse.put("message", "Invalid parameters.");
										 jsonresponse.put("response_status", "error");
									 }
								 }
								 else
								 {
									 System.err.println("Endpoint(): couldn't find globalvar for imp_targets");
									 jsonresponse.put("message", "Couldn't process new impression.");
									 jsonresponse.put("response_status", "error");
								 }	
							 }
							 else
							 {
								 System.err.println("Endpoint: noteImpression disallowed source_category=" + source_category);
								 jsonresponse.put("message", "Invalid parameters.");
								 jsonresponse.put("response_status", "error");
							 }
						 }
						 else
						 {
							 System.err.println("Endpoint(): couldn't find globalvar for imp_source_categories");
							 jsonresponse.put("message", "Couldn't process new impression.");
							 jsonresponse.put("response_status", "error");
						 }
					 }
				 }
				 else if(method.equals("noteConversion"))
				 {
					 String id = request.getParameter("id");
					 if(id == null)
					 {
						 jsonresponse.put("message", "id required.");
						 jsonresponse.put("response_status", "error");
					 }
					 else
					 {	 
						 ImpressionItem pi = mapper.load(ImpressionItem.class, id, dynamo_config);
						 if(pi != null)
						 {
							 pi.setConversionMSFE(timestamp_at_entry);
							 mapper.save(pi);
							 jsonresponse.put("response_status", "success");
							 jsonresponse.put("target", pi.getTarget());
							 GlobalvarItem email_admin_on_conversion_gvi = mapper.load(GlobalvarItem.class, "email_admin_on_conversion", dynamo_config);
							 if(email_admin_on_conversion_gvi != null && email_admin_on_conversion_gvi.getNumberValue() == 1)
							 {
								 String body = "Conversion! source=" + pi.getSourceCategory() + " target=" + pi.getTarget();
								 String htmlbody = "Conversion! source=" + pi.getSourceCategory() + " target=" + pi.getTarget();
								 SimpleEmailer se = new SimpleEmailer("Conversion! source=" + pi.getSourceCategory() + " target=" + pi.getTarget(), body, htmlbody, "info@words4chrome.com", "info@words4chrome.com");
								 se.start();
							 }
						 }
						 else
						 {
							 jsonresponse.put("message", "id not found.");
							 jsonresponse.put("response_status", "error");
						 }
					 }
				 }
				 /***
				  *    ___  ___ _____ _____ _   _ ___________  _____  ______ _____ _____     _____  _____ _____ _____  ___   _       _____ _____ _   __ _____ _   _ 
				  *    |  \/  ||  ___|_   _| | | |  _  |  _  \/  ___| | ___ \  ___|  _  |   /  ___||  _  /  __ \_   _|/ _ \ | |     |_   _|  _  | | / /|  ___| \ | |
				  *    | .  . || |__   | | | |_| | | | | | | |\ `--.  | |_/ / |__ | | | |   \ `--. | | | | /  \/ | | / /_\ \| |       | | | | | | |/ / | |__ |  \| |
				  *    | |\/| ||  __|  | | |  _  | | | | | | | `--. \ |    /|  __|| | | |    `--. \| | | | |     | | |  _  || |       | | | | | |    \ |  __|| . ` |
				  *    | |  | || |___  | | | | | \ \_/ / |/ / /\__/ / | |\ \| |___\ \/' /_  /\__/ /\ \_/ / \__/\_| |_| | | || |____   | | \ \_/ / |\  \| |___| |\  |
				  *    \_|  |_/\____/  \_/ \_| |_/\___/|___/  \____/  \_| \_\____/ \_/\_(_) \____/  \___/ \____/\___/\_| |_/\_____/   \_/  \___/\_| \_/\____/\_| \_/
				  *                                                                                                                                                 
				  */
				 else if(method.equals("login")) // user does not have valid screenname/tat, but has a valid social access token
				 {
					// System.out.println("Endpoint.login() begin");
					String social_access_token = request.getParameter("social_access_token");
					String login_type = request.getParameter("login_type");
					if(social_access_token == null || social_access_token.isEmpty())
					{
						jsonresponse.put("response_status", "error");
						jsonresponse.put("message", "No token supplied.");
					}
					else if(login_type == null || login_type.isEmpty())
					{
						jsonresponse.put("message", "This method requires a login_type value");
						jsonresponse.put("response_status", "error");
					}
					else
					{
						if(login_type.equals("facebook"))
							jsonresponse = loginWithFacebookOrShowRegistration(social_access_token); 
						else if(login_type.equals("google"))
							jsonresponse = loginWithGoogleOrShowRegistration(social_access_token);
						else
						{	jsonresponse.put("message", "Unknown login_type value."); jsonresponse.put("response_status", "error"); }
					}
					// System.out.println("Endpoint.login() end");
				 }
				 else if(method.equals("getSocialPicture")) // user does not have valid screenname/tat, but has a valid social access token
				 {
					// System.out.println("Endpoint.getSocialPicture() begin");
					String social_access_token = request.getParameter("social_access_token");
					String login_type = request.getParameter("login_type");
					if(social_access_token == null || social_access_token.isEmpty())
					{
						jsonresponse.put("response_status", "error");
						jsonresponse.put("message", "No token supplied.");
					}
					else if(login_type == null || login_type.isEmpty())
					{
						jsonresponse.put("message", "This method requires a login_type value");
						jsonresponse.put("response_status", "error");
					}
					else
					{
						if(login_type.equals("facebook"))
						{
							jsonresponse = getFacebookPicture(social_access_token); 
						}
						else if(login_type.equals("google"))
						{
							jsonresponse = getGooglePicture(social_access_token);
						}
						else
						{	jsonresponse.put("message", "Unknown login_type value."); jsonresponse.put("response_status", "error"); }
					}
					// System.out.println("Endpoint.getSocialPicture() end");
				 }
				 else if (method.equals("createUser")) 
				 { 
					// System.out.println("Endpoint.createUser() begin");
					String login_type = request.getParameter("login_type");
					String social_access_token = request.getParameter("social_access_token"); // if native, this can be null
					String picture = request.getParameter("picture");
					String email = request.getParameter("email");
					String screenname = request.getParameter("screenname");
					String useragent = request.getParameter("useragent");
					String password = request.getParameter("password");
					
					EmailValidator ev = EmailValidator.getInstance(false); // local addresses (@localhost, eg) should not be allowed.
					
					// validate the incoming pieces of information, then proceed
					// Seems better to check this stuff here (before the google/facebook API calls) rather than (or maybe in addition to) the TTU.createUser method
					
					if(login_type == null || login_type.isEmpty())
					{	
						jsonresponse.put("message", "This method requires a login_type value");	jsonresponse.put("response_status", "error");	
					}
					else if(useragent == null || useragent.isEmpty())
					{	
						jsonresponse.put("response_status", "error"); jsonresponse.put("message", "No useragent supplied."); 
					}
					else if(email == null)
					{
						jsonresponse.put("response_status", "error"); jsonresponse.put("message", "Supplied email is null."); 
					}
					else if(email.isEmpty())
					{
						jsonresponse.put("response_status", "error"); jsonresponse.put("message", "Supplied email is empty."); 
					}
					else if(!ev.isValid(email))
					{	
						jsonresponse.put("response_status", "error"); jsonresponse.put("message", "Email structure is invalid. email=" + email);	
					}
					else if(!isValidScreenname(screenname))
					{	
						jsonresponse.put("response_status", "error"); jsonresponse.put("message", "Screenname is invalid. Must be 3-15 letters & digits, starting with a letter.");	
					}
					else if(password == null)
					{	
						jsonresponse.put("response_status", "error"); jsonresponse.put("message", "Password cannot be null.");	
					}
					else if(!isValidPassword(password))
					{	
						jsonresponse.put("response_status", "error"); jsonresponse.put("message", "Password is invalid. Must be 8-20 letters & digits, or !@-#$%*_.");	
					}
					else if(!Global.isValidPicture(picture))
					{
						jsonresponse.put("response_status", "error"); jsonresponse.put("message", "Invalid picture url."); 
					}
					else if(email.endsWith("@words4chrome.com") && !email.substring(0,email.indexOf("@words4chrome.com")).equals(screenname))
					{
						jsonresponse.put("response_status", "error"); jsonresponse.put("message", "If creating an @words4chrome.com user, email must be screenname@words4chrome.com");
					}
					else
					{
						if(login_type != null && login_type.equals("google"))
						{	
							if(social_access_token == null || social_access_token.isEmpty())
							{	
								jsonresponse.put("response_status", "error"); jsonresponse.put("message", "No token supplied."); 
							}
							else
							{	
								 JSONObject profile_info_jo = new JSONObject();
								 HttpClient httpclient = new DefaultHttpClient();
								 try {
									 HttpGet httpget = new HttpGet("https://www.googleapis.com/plus/v1/people/me?access_token=" + social_access_token);
									 ResponseHandler<String> responseHandler = new BasicResponseHandler();
									 String responseBody = httpclient.execute(httpget, responseHandler);
									 profile_info_jo = new JSONObject(responseBody);
									 
									 String email_from_profile = null;
									 boolean google_profile_contained_valid_email = false;
									 if(profile_info_jo.has("emails"))
									 {
										 JSONArray emails_ja = profile_info_jo.getJSONArray("emails");
										 JSONObject current_email_jo = null;
										 for(int x = 0; x < emails_ja.length(); x++)
										 {
											 current_email_jo = emails_ja.getJSONObject(x);
											 if(current_email_jo.has("type") && current_email_jo.get("type").equals("account") && current_email_jo.has("value"))
											 {
												 email_from_profile = current_email_jo.getString("value");
												 if(email_from_profile.equals(email)) // the email the user is asserting along with their access_token must equal what comes back from google using that access_token
													 google_profile_contained_valid_email = true;
												 break;
											 }
										 }
									
										 if(google_profile_contained_valid_email)
										 {
											 WordsCore wc = new WordsCore(mapper, dynamo_config, client);
											 jsonresponse = wc.createUser(email, screenname, password, picture, "google", useragent, request.getRemoteAddr()); 
										 }
										 else // found emails array, but none of type "account". Pretty sure "account" (the person's main google email address) is 100% required, so this should never happen.
										 {
											 System.err.println("Endpoint method=createUser: Got access_token, and profile contained emails array, but none of type \"account\".");
											 jsonresponse.put("response_status", "error");
											 jsonresponse.put("message", "Got access_token, and profile contained emails array, but none of type \"account\".");
											 jsonresponse.put("login_type", "google");
										 } 
									 }
									 else
									 {
										 System.err.println("Endpoint method=createUser: Got access_token, but google profile did not contain \"emails\" array. Could not create user.");
										 jsonresponse.put("response_status", "error");
										 jsonresponse.put("message", "Got access_token, but google profile did not contain \"emails\" array. Could not create user.");
										 jsonresponse.put("login_type", "google");
									 }
								 } 
								 catch(ClientProtocolException cpe)  
								 {	
									 System.err.println("Endpoint.createUser(): ClientProtocolException: " + cpe.getMessage());
									 // if we've gotten here, there's a near-certain chance the user's token is no longer valid. Need to return a "delete cookies" signal.
									 jsonresponse.put("response_status", "error");
									 jsonresponse.put("message", "Your Google token is no longer valid. Please try again.");
									 jsonresponse.put("error_code", "0000");
									 jsonresponse.put("login_type", "google");
								 }
							}
						}
						else if(login_type != null && login_type.equals("facebook"))
						{	
							if(social_access_token == null || social_access_token.isEmpty())
							{	
								jsonresponse.put("response_status", "error"); jsonresponse.put("message", "No token supplied."); 
							}
							else
							{	
								JSONObject profile_info_jo = new JSONObject();
								HttpClient httpclient = new DefaultHttpClient();
								try {
									 HttpGet httpget = new HttpGet("https://graph.facebook.com/me?access_token=" + social_access_token);
									 ResponseHandler<String> responseHandler = new BasicResponseHandler();
									 String responseBody = httpclient.execute(httpget, responseHandler);
									 profile_info_jo = new JSONObject(responseBody);
								
									 if(profile_info_jo.getBoolean("verified") == true)
									 {	
										 boolean facebook_profile_contained_valid_email = false;
										 String email_from_profile = profile_info_jo.getString("email");
										 if(email_from_profile.equals(email)) // the email the user is asserting along with their access_token must equal what comes back from google using that access_token
											 facebook_profile_contained_valid_email = true;
										 if(facebook_profile_contained_valid_email)
										 {
											 WordsCore wc = new WordsCore(mapper, dynamo_config, client);
											 jsonresponse = wc.createUser(email, screenname, password, picture, "facebook", useragent, request.getRemoteAddr()); 
										 }
										 else
										 {
											 System.err.println("Endpoint method=createUser: Facebook profile did not contain valid email address.");
											 jsonresponse.put("response_status", "error");
											 jsonresponse.put("message", "Facebook profile did not contain valid email address.");
											 jsonresponse.put("login_type", "facebook");
										 } 
									 }
									 else
									 {
										 jsonresponse.put("response_status", "error");
										 jsonresponse.put("message", "Sorry, the email associated with your Facebook account is not verified. Please verify it with Facebook and try again.");
										 jsonresponse.put("login_type", "facebook");
									 }
								 } 
								 catch(ClientProtocolException cpe)  
								 {	
									 System.err.println("Endpoint.createUser(): ClientProtocolException: " + cpe.getMessage());
									 // if we've gotten here, there's a near-certain chance the user's token is no longer valid. Need to return a "delete cookies" signal.
									 jsonresponse.put("response_status", "error");
									 jsonresponse.put("message", "Your Facebook token is no longer valid. Please try again.");
									 jsonresponse.put("error_code", "0000");
									 jsonresponse.put("login_type", "facebook");
								 }
							}
						}
						else if(login_type != null && login_type.equals("words"))
						{	
							 WordsCore wc = new WordsCore(mapper, dynamo_config, client);
							 jsonresponse = wc.createUser(email, screenname, password, picture, "words", useragent, request.getRemoteAddr()); 
						}
						else // allow only "google", "facebook" and native
						{
							
							 jsonresponse.put("response_status", "error");
							 jsonresponse.put("message", "Unknown login type. login_type=" + login_type);
						}
					}
					// System.out.println("Endpoint.createUser() end");
				}
				 
				 /***
				  *    ___  ___ _____ _____ _   _ ___________  _____  ______ _____ _____     _   _ _____ ___________    ___  _   _ _____ _   _ 
				  *    |  \/  ||  ___|_   _| | | |  _  |  _  \/  ___| | ___ \  ___|  _  |   | | | /  ___|  ___| ___ \  / _ \| | | |_   _| | | |
				  *    | .  . || |__   | | | |_| | | | | | | |\ `--.  | |_/ / |__ | | | |   | | | \ `--.| |__ | |_/ / / /_\ \ | | | | | | |_| |
				  *    | |\/| ||  __|  | | |  _  | | | | | | | `--. \ |    /|  __|| | | |   | | | |`--. \  __||    /  |  _  | | | | | | |  _  |
				  *    | |  | || |___  | | | | | \ \_/ / |/ / /\__/ / | |\ \| |___\ \/' /_  | |_| /\__/ / |___| |\ \  | | | | |_| | | | | | | |
				  *    \_|  |_/\____/  \_/ \_| |_/\___/|___/  \____/  \_| \_\____/ \_/\_(_)  \___/\____/\____/\_| \_| \_| |_/\___/  \_/ \_| |_/
				  *                                                                                                                            
				  *                                                                                                                            
				  */
				 else if (method.equals("addComment") || method.equals("separateHostname") || method.equals("combineHostname") || method.equals("setSignificantQSP") || method.equals("likeHPQSP") || method.equals("haveILikedThisHPQSP") ||             // methods req url
							method.equals("likeHostname") || method.equals("haveILikedThisHostname") ||  method.equals("followPage") || method.equals("unfollowPage") || method.equals("amIFollowingThisPage") ||     // methods req url
						 
							method.equals("addCommentLikeOrDislike") || method.equals("hideComment") || method.equals("nukeComment") || 					// methods req comment id
							method.equals("haveILikedThisComment") || method.equals("haveIDislikedThisComment") ||  // methods req comment id
							
							method.equals("getUserSelf") || method.equals("getUserByScreenname") || method.equals("setUserPreference") || method.equals("getMyComments") ||  // user self methods
							method.equals("removeItemFromActivityIds") || method.equals("savePicture") || method.equals("resetActivityCount") || method.equals("setProvisionalEmail") ||
							method.equals("confirmEmailAddressWithCode") || method.equals("removeEmail") || method.equals("changePassword") || method.equals("noteSocialShare"))	// user self methods
				 {
					 // for all of these methods, check email/this_access_token. Weak check first (to avoid database hits). Then check database.
					 String screenname = request.getParameter("screenname"); // the requester's email
					 String this_access_token = request.getParameter("this_access_token"); // the requester's auth
					 if(!(screenname == null || screenname.isEmpty()) && !(this_access_token == null || this_access_token.isEmpty())) 
					 {
						// both weren't null or empty
						// if only email is null or empty respond with code "0000" to clear out the malformed credentials
						if(!(screenname == null || screenname.isEmpty()))
						{
							// otherwise, continue to user retrieval
							UserItem useritem = mapper.getUserItemFromScreenname(screenname);
							if(useritem != null)
							{	
								if(useritem.isValid(this_access_token)) 
								{	
									// this method used to require a valid URL. Now it only requires one IF it's not a reply. This is now checked inside WC.createComment instead of here, if applicable
									if (method.equals("addComment")) // email, this_access_token, target_email (of user to get) // also an admin method
									{
										 String url = request.getParameter("url"); // if this is a reply, this url value doesn't matter. The url of the parent will be used instead.
										 // this method handles all comment addition, even replies to other comments
										 // "parent" must not be null and is either the url (which will be standardized below) or the id of the parent comment
										 double silence_threshold = -5.0;
										 GlobalvarItem silence_threshold_gvi = mapper.load(GlobalvarItem.class, "silence_threshold", dynamo_config);
										 if(silence_threshold_gvi != null)
											 silence_threshold = silence_threshold_gvi.getNumberValue();
										 if(useritem.getRatingInWindow() <= silence_threshold)
										 {	 
											 jsonresponse.put("response_status", "error");
											 jsonresponse.put("message", "Your rating is too low to create comments.");
										 }
										 else
										 {	 
											// this throttling mechanism chokes the user off over time logrithmically like so: 
											 // 8/15min, 12/30min, 14/45min, 15/60min 
											 // If the user is above the threshold in any of these tests, they get choked off. 
											 // This mechanism allows for small bursts (where the user might be having a back-and-forth),
											 // but if that happens, they're only allowed 4 more in the next 15 minutes, 2 in the following 15 and one more in the last 15.
											 String text = request.getParameter("text");
											 if(text != null && text.length() > 500)
											 {
												 jsonresponse.put("response_status", "error");
												 jsonresponse.put("message", "Comments must be 500 characters or less."); 
											 }
											 else if(text == null)
											 {
												 jsonresponse.put("response_status", "error");
												 jsonresponse.put("message", "text param was null"); 
											 }
											 else if(text.isEmpty())
											 {
												 jsonresponse.put("response_status", "error");
												 jsonresponse.put("message", "text param was empty"); 
											 }
											 else
											 {	 
												 TreeSet<CommentItem> comments = useritem.getCommentsAuthored(60, mapper, dynamo_config);
												 boolean throttle = false;
												 
												 if(comments != null && !useritem.getPermissionLevel().equals("admin")) // if the user has no comments, skip this. Obviously they don't need to be throttled.
												 {	 
													 TreeSet<Long> ordered_comment_longs = new TreeSet<Long>();
													 Iterator<CommentItem> ci_it = comments.iterator();
													 while(ci_it.hasNext())
														 ordered_comment_longs.add(ci_it.next().getMSFE());
													 Iterator<Long> d_it = ordered_comment_longs.descendingIterator();
													 int limit15 = 8;
													 int limit30 = 12;
													 int limit45 = 14;
													 int limit60 = 15;
													 long current_ts = 0L;
													 Calendar windowcal = Calendar.getInstance();
													 windowcal.add(Calendar.MINUTE, -15);
													 long fifteenago = windowcal.getTimeInMillis();
													 windowcal.add(Calendar.MINUTE, -15);
													 long thirtyago = windowcal.getTimeInMillis();
													 windowcal.add(Calendar.MINUTE, -15);
													 long fourtyfiveago = windowcal.getTimeInMillis();
													 windowcal.add(Calendar.MINUTE, -15);
													 long sixtyago = windowcal.getTimeInMillis();
													 int numin15 = 0;
													 int numin30 = 0;
													 int numin45 = 0;
													 int numin60 = 0;
													 while(d_it.hasNext() && numin15 < limit15 && numin30 < limit30 && numin45 < limit45 && numin60 < limit60)
													 {
														 current_ts = d_it.next();
														 if(current_ts > fifteenago)
														 	 numin15++;
														 if(current_ts > thirtyago)
															 numin30++;
														 if(current_ts > fourtyfiveago)
															 numin45++;
														 if(current_ts > sixtyago)
															 numin60++;
														 if(current_ts <= sixtyago) // as soon as we find one older than 60, break because they're ordered.
															 break;
													 }
													 if(numin15 >= limit15 || numin30 >= limit30 || numin45 >= limit45 || numin60 >= limit60)
													 {
														 throttle = true;
													 }
												 }
												
												 if(throttle)
												 {
													 System.err.println("TODO: USER=" + useritem.getScreenname() + " IS TRYING TO WRITE TOO MANY COMMENTS TOO QUICKLY.");
													 jsonresponse.put("response_status", "error");
													 jsonresponse.put("message", "Rate limit surpassed. You are writing comments too quickly. Please curb your enthusiasm.");
												 }
												 else
												 {	 
													 String parent = request.getParameter("parent"); // url or comment id
													 if(parent == null || parent.isEmpty())
													 {
														 jsonresponse.put("response_status", "error");
														 jsonresponse.put("message", "parent value required. Use the url for toplevel comments.");
													 }
													 else
													 {
														 // PASS THIS OFF TO WC for comment creation, url creation, thread creation and hostname creation.
														 if(parent.indexOf(".") != -1)
														 	 parent = null;
														 WordsCore wc = new WordsCore(mapper, dynamo_config, client);
														 jsonresponse = wc.createComment(useritem, text, url, request.getRemoteAddr(), parent); 
													 }
												 }
											 }
										 }
									}
									/***
									 *    ___  ___ _____ _____ _   _ ___________  _____  ______ _____ _____    ___  _   _ _____ _   _           _   _______ _     
									 *    |  \/  ||  ___|_   _| | | |  _  |  _  \/  ___| | ___ \  ___|  _  |  / _ \| | | |_   _| | | |    _    | | | | ___ \ |    
									 *    | .  . || |__   | | | |_| | | | | | | |\ `--.  | |_/ / |__ | | | | / /_\ \ | | | | | | |_| |  _| |_  | | | | |_/ / |    
									 *    | |\/| ||  __|  | | |  _  | | | | | | | `--. \ |    /|  __|| | | | |  _  | | | | | | |  _  | |_   _| | | | |    /| |    
									 *    | |  | || |___  | | | | | \ \_/ / |/ / /\__/ / | |\ \| |___\ \/' / | | | | |_| | | | | | | |   |_|   | |_| | |\ \| |____
									 *    \_|  |_/\____/  \_/ \_| |_/\___/|___/  \____/  \_| \_\____/ \_/\_\ \_| |_/\___/  \_/ \_| |_/          \___/\_| \_\_____/
									 *                                                                                                                            
									 *                                                                                                                            
									 */
									
									if(method.equals("separateHostname") || method.equals("combineHostname") || method.equals("setSignificantQSP") || method.equals("likeHPQSP") || method.equals("haveILikedThisHPQSP") || 
											method.equals("likeHostname") || method.equals("haveILikedThisHostname") ||  method.equals("followPage") || method.equals("unfollowPage") || method.equals("amIFollowingThisPage"))
									{
										 String url = request.getParameter("url");
										 if(url != null && !url.isEmpty())
										 {
											 if(url != null && url.indexOf("#") != -1)
												 url = url.substring(0, url.indexOf("#"));
											 if(url != null && (url.indexOf("/", url.indexOf("://") + 3) == -1)) // if of the form http://www.yahoo.com, make it http://www.yahoo.com/ 
													url = url + "/";
											 if(Global.isValidURLFormation(url, true))
											 {
												 if (method.equals("separateHostname")) 
												 {
													 if(useritem.getPermissionLevel().equals("admin"))
													 {
														 String hostname_str = Global.getStandardizedHostnameFromURL(url);
														 HostnameItem hostnameitem = mapper.load(HostnameItem.class, hostname_str, dynamo_config);
														 if(hostnameitem == null)
														 {
															 hostnameitem = new HostnameItem();
															 hostnameitem.setHostname(Global.getStandardizedHostnameFromURL(url));
															 hostnameitem.setOriginalURL(url);
														 }
														 hostnameitem.setSeparated(true);
														 mapper.save(hostnameitem);
														 jsonresponse.put("message", "Hostname separated."); 
														 jsonresponse.put("response_status", "success");	 
													 }
													 else
													 {
														 jsonresponse.put("message", "Only admins can do that.");
														 jsonresponse.put("response_status", "error");
													 }
												 }
												 else if (method.equals("combineHostname")) 
												 {
													 if(useritem.getPermissionLevel().equals("admin"))
													 {
														 String hostname_str = Global.getStandardizedHostnameFromURL(url);
														 HostnameItem hostnameitem = mapper.load(HostnameItem.class, hostname_str, dynamo_config);
														 if(hostnameitem == null)
														 {
															 jsonresponse.put("response_status", "success");
															 jsonresponse.put("message", "Hostname already combined."); 
														 }
														 else
														 {	 
															 hostnameitem.setSeparated(false);
															 mapper.save(hostnameitem);
															 jsonresponse.put("message", "Hostname combined."); 
															 jsonresponse.put("response_status", "success");	
														 }
													 }
													 else
													 {
														 jsonresponse.put("message", "Only admins can do that.");
														 jsonresponse.put("response_status", "error");
													 }
												 }
												 else if (method.equals("setSignificantQSP")) 
												 {
													 System.out.println("endpoint.setSignificantQSP url=" + url);
													 if(useritem.getPermissionLevel().equals("admin"))
													 {
														 String sqsp = request.getParameter("sqsp");
														 if(sqsp == null || sqsp.isEmpty())
														 {
															 jsonresponse.put("message", "Method requires a valid sqsp value.");
															 jsonresponse.put("response_status", "error");
														 }
														 else
														 {
															 WordsCore wc = new WordsCore(mapper, dynamo_config, client);
															 jsonresponse = wc.setSignificantQSPs(url, new String[]{sqsp}); // the WordsCore.setSignificantQSPs purportedly takes multiple strings, but only supports one for now.
														 }
													 }
													 else
													 {
														 jsonresponse.put("message", "Only admins can do that.");
														 jsonresponse.put("response_status", "error");
													 }
												 }
												 else if (method.equals("amIFollowingThisPage"))
												 {
													 //System.out.println("Endpoint.amIFollowingThisPage() begin");
													 String significant_designation = Global.getHPQSPAccordingToThisURL(url, mapper);
													 FollowItem fi = mapper.load(FollowItem.class, significant_designation, useritem.getId());
													 jsonresponse.put("response_status", "success");
													 if(fi == null)
														 jsonresponse.put("response_value", false);
													 else
														 jsonresponse.put("response_value", true);
													 //System.out.println("Endpoint.amIFollowingThisPage() end");
												 }
												 else if (method.equals("followPage"))
												 {
													 //System.out.println("Endpoint.followPage() begin");
													 String significant_designation = Global.getHPQSPAccordingToThisURL(url, mapper);
													 FollowItem fi = mapper.load(FollowItem.class, significant_designation, useritem.getId());
													 if(fi == null)
													 {	 
														 fi = new FollowItem();
														 fi.setSignificantDesignation(significant_designation);
														 fi.setUserId(useritem.getId());
														 mapper.save(fi);
														 jsonresponse.put("response_status", "success");
													 }
													 else
													 {
														 jsonresponse.put("message", "You are already following this page.");
														 jsonresponse.put("response_status", "error");
													 }		
													// System.out.println("Endpoint.followPage() end");
												 }
												 else if (method.equals("unfollowPage"))
												 {
													 //System.out.println("Endpoint.unfollowPage() begin");
													 String significant_designation = Global.getHPQSPAccordingToThisURL(url, mapper);
													 FollowItem fi = mapper.load(FollowItem.class, significant_designation, useritem.getId());
													 if(fi == null)
													 {	 
														 jsonresponse.put("message", "You aren't following this page.");
														 jsonresponse.put("response_status", "error");
													 }
													 else
													 {
														 mapper.delete(fi);
														 jsonresponse.put("response_status", "success");
													 }		 
													 // System.out.println("Endpoint.unfollowPage() end");
												 }
												 else if (method.equals("likeHPQSP"))
												 {
													 WordsCore wc = new WordsCore(mapper, dynamo_config, client);
													 jsonresponse = wc.createHPQSPLike(useritem, url);
												 }
												 else if (method.equals("haveILikedThisHPQSP"))
												 {
													 //System.out.println("Endpoint.haveILikedThisHPQSP() begin");
													 String hpqsp = Global.getHPQSPAccordingToThisURL(url, mapper);
													 jsonresponse.put("response_status", "success");
													 jsonresponse.put("response_value", useritem.hasLikedHPQSP(hpqsp, mapper, dynamo_config));			 
													 //System.out.println("Endpoint.haveILikedThisHPQSP() end");
												 }
												 else if (method.equals("likeHostname"))
												 {
													 WordsCore wc = new WordsCore(mapper, dynamo_config, client);
													 jsonresponse = wc.createHostnameLike(useritem, url);
												 }
												 else if (method.equals("haveILikedThisHostname"))
												 {
													 //System.out.println("Endpoint.haveILikedThisHostname() begin");
													 String hostname = Global.getStandardizedHostnameFromURL(url);
													 jsonresponse.put("response_status", "success");
													 jsonresponse.put("response_value", useritem.hasLikedHostname(hostname, mapper, dynamo_config));	
													 //System.out.println("Endpoint.haveILikedThisHostname() end");
												 }
											 }
											 else
											 {
												 jsonresponse.put("message", "Invalid URL formation");
												 jsonresponse.put("response_status", "error");
											 }
										 }
										 else
										 {
											 jsonresponse.put("message", "Url was null or empty");
											 jsonresponse.put("response_status", "error");
										 }
										
									}
									else if (method.equals("getUserSelf"))
									{
										JSONObject user_jo = null;
										useritem.setThisAccessTokenExpires(timestamp_at_entry + 31556900000L);
										 Calendar cal = Calendar.getInstance();
										 long now = cal.getTimeInMillis();
										 useritem.setSeen(now);
										 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
										 sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
										 useritem.setSeenHumanReadable(sdf.format(timestamp_at_entry));
										 useritem.setLastIPAddress(request.getRemoteAddr()); // necessary for spam and contest fraud prevention
										 mapper.save(useritem);
										 boolean get_email = true;
										 boolean get_this_access_token = true; 
										 boolean get_activity_ids = true; 
										 boolean get_email_preferences = true; 
										 boolean get_notification_count = true;
										 boolean get_seen = true;
										 user_jo = useritem.getAsJSONObject(get_email, get_this_access_token, get_activity_ids, get_email_preferences, get_notification_count, get_seen, client, mapper, dynamo_config);
										 jsonresponse.put("response_status", "success");
										 jsonresponse.put("user_jo", user_jo);
									 }
									 else if (method.equals("addCommentLikeOrDislike")) // email, this_access_token, target_email (of user to get) // also an admin method
									 {
										// this method handles all comment addition, even replies to other comments
										 // "parent" must not be null and must be the id of the parent comment
										 double silence_threshold = -5.0;
										 GlobalvarItem silence_threshold_gvi = mapper.load(GlobalvarItem.class, "silence_threshold", dynamo_config); // must be whole number
										 if(silence_threshold_gvi != null)
											 silence_threshold = silence_threshold_gvi.getNumberValue(); // must be whole number
										 
										 String like_or_dislike = request.getParameter("like_or_dislike");
										 double dislike_threshold = 0.1;
										 if(like_or_dislike.equals("dislike"))
										 {
											 GlobalvarItem dislike_threshold_gvi = mapper.load(GlobalvarItem.class, "dislike_threshold", dynamo_config); // must be whole number
											 if(dislike_threshold_gvi != null)
												 dislike_threshold = dislike_threshold_gvi.getNumberValue(); // must be whole number
										 }
										 
										 if(useritem.getRatingInWindow() <= silence_threshold)
										 {	 
											 jsonresponse.put("response_status", "error");
											 jsonresponse.put("message", "Your rating is too low to create comment feedback.");
										 }
										/* else if(like_or_dislike.equals("dislike") && useritem.getRating() < dislike_threshold)
										 {
											 jsonresponse.put("response_status", "error");
											 jsonresponse.put("message", "You must have a rating of " + dislike_threshold + " to dislike comments. (2x up vs down)");
										 }*/
										 else
										 {	 
											// this throttling mechanism chokes the user off over time logrithmically like so: 
											 // 8/15min, 12/30min, 14/45min, 15/60min 
											 // If the user is above the threshold in any of these tests, they get choked off. 
											 // This mechanism allows for small bursts (where the user might be having a back-and-forth),
											 // but if that happens, they're only allowed 4 more in the next 15 minutes, 2 in the following 15 and one more in the last 15.
											 
											 TreeSet<LikeItem> likeitems = useritem.getLikesAuthored(60, mapper, dynamo_config);
											 TreeSet<DislikeItem> dislikeitems = useritem.getDislikesAuthored(60, mapper, dynamo_config);
											 TreeSet<Long> ordered_longs = new TreeSet<Long>();
											 if(!(likeitems == null && dislikeitems == null))
											 { 
												 if(likeitems != null)
												 {
													 Iterator<LikeItem> li_it = likeitems.iterator();
													 while(li_it.hasNext())
													 {
														 ordered_longs.add(li_it.next().getMSFE());
													 }
												 }
												 if(dislikeitems != null)
												 {	 
													 Iterator<DislikeItem> di_it = dislikeitems.iterator();
													 while(di_it.hasNext())
													 {
														 ordered_longs.add(di_it.next().getMSFE());
													 }
												 }
											 }
											 
											 Iterator<Long> d_it = ordered_longs.descendingIterator(); // might be empty here. No problem, though.
											 int limit15 = 16;
											 int limit30 = 24;
											 int limit45 = 28;
											 int limit60 = 30;
											 long current_ts = 0L;
											 Calendar windowcal = Calendar.getInstance();
											 windowcal.add(Calendar.MINUTE, -15);
											 long fifteenago = windowcal.getTimeInMillis();
											 windowcal.add(Calendar.MINUTE, -15);
											 long thirtyago = windowcal.getTimeInMillis();
											 windowcal.add(Calendar.MINUTE, -15);
											 long fourtyfiveago = windowcal.getTimeInMillis();
											 windowcal.add(Calendar.MINUTE, -15);
											 long sixtyago = windowcal.getTimeInMillis();
											 int numin15 = 0;
											 int numin30 = 0;
											 int numin45 = 0;
											 int numin60 = 0;
											 while(d_it.hasNext() && numin15 < limit15 && numin30 < limit30 && numin45 < limit45 && numin60 < limit60)
											 {
												 current_ts = d_it.next();
												 //System.out.println("--\ncurrent_ts=" + current_ts + "\n   and then=" + then);
												 if(current_ts > fifteenago)
												 	 numin15++;
												 if(current_ts > thirtyago)
													 numin30++;
												 if(current_ts > fourtyfiveago)
													 numin45++;
												 if(current_ts > sixtyago)
													 numin60++;
												 if(current_ts <= sixtyago) // as soon as we find one older than 60, break because they're ordered.
													 break;
											 }
											 if(numin15 >= limit15 || numin30 >= limit30 || numin45 >= limit45 || numin60 >= limit60)
											 {
												 System.err.println("TODO: USER=" + useritem.getScreenname() + " IS TRYING TO LIKE/DISLIKE TOO MUCH STUFF.");
												 jsonresponse.put("response_status", "error");
												 jsonresponse.put("message", "Rate limit surpassed. You are liking/disliking too much. Please curb your enthusiasm.");
											 }
											 else
											 {	 
												 String id = request.getParameter("id");
												 CommentItem commentitem = mapper.load(CommentItem.class, id, dynamo_config); // try to instantiate
												 if(commentitem != null) // comment was, indeed, found in the database
												 {	
													 if(commentitem.getHidden())
													 {
														 jsonresponse.put("message", "The comment you are trying to like or dislike is hidden. This is not allowed.");	
														 jsonresponse.put("response_status", "error");
													 }
													 else
													 {	 
														 UserItem comment_author = mapper.load(UserItem.class, commentitem.getAuthorId(), dynamo_config);
														 if(like_or_dislike.equals("dislike") && comment_author.getPermissionLevel().equals("admin"))
														 {
															 jsonresponse.put("message", "Sorry, the author of this comment can't be downvoted.");	
															 jsonresponse.put("response_status", "error");
														 }
														 else
														 { 
															 WordsCore wc = new WordsCore(mapper, dynamo_config, client);
															 if(like_or_dislike.equals("like"))
																 jsonresponse = wc.createCommentLike(useritem, commentitem, request.getRemoteAddr());
															 else if(like_or_dislike.equals("dislike"))
																 jsonresponse = wc.createCommentDislike(useritem, commentitem, request.getRemoteAddr());
															 else
															 {
																 jsonresponse.put("message", "like_or_dislike value must be, ahem, \"like\" or \"dislike\"");	
																 jsonresponse.put("response_status", "error");
															 }	
														 }
													 }
												 }
												 else
												 {
													 jsonresponse.put("response_status", "error");
													 jsonresponse.put("message", "endpoint.doGet() method=addCommentLikeOrDislike: Error: comment didn't exist in db");
												 }
											 }	 
										 }
									 }
									 else if (method.equals("hideComment")) // email, this_access_token, target_email (of user to get) // also an admin method
									 {
										 String id = request.getParameter("id");
										 CommentItem c = mapper.load(CommentItem.class, id, dynamo_config); // try to instantiate
										 
										 if(c == null)
										 {
											 jsonresponse.put("message", "Error hiding comment. Comment id doesn't exist."); 
											 jsonresponse.put("response_status", "error");
										 }
										 else
										 {		 
											 // This must be the user's comment or the user must be an admin
											 boolean proceed = false;
											 if(c.getAuthorId().equals(useritem.getId()) || useritem.getPermissionLevel().equals("admin"))
												 proceed = true;
											 
											 if(!proceed)
											 {
												 jsonresponse.put("message", "Error hiding comment. You may only hide your own comments."); 
												 jsonresponse.put("response_status", "error");
											 }
											 else
											 {
												 c.setHidden(true);
												 mapper.save(c);
												 jsonresponse.put("comment", c.getAsJSONObject(mapper, dynamo_config, true)); // get author stuff even if hidden. This person is the owner.
												 jsonresponse.put("response_status", "success"); // get fuller version
											 }
										 }
									 }
									 else if (method.equals("nukeComment")) // an admin method. Nukes comment + all replies, likes and dislikes. Use with caution since
									 {	 									// these objects likely exist in activity feeds elsewhere. For testing and emergencies only.
										 if(!useritem.getPermissionLevel().equals("admin"))
										 {
											 jsonresponse.put("message", "Error deleting comment+children. Only admins can do this."); 
											 jsonresponse.put("response_status", "error");
										 }
										 else
										 {
											 String id = request.getParameter("id");
											 CommentItem c = mapper.load(CommentItem.class, id, dynamo_config); // try to instantiate
											 if(c == null)
											 {
												 jsonresponse.put("message", "Error deleting comment+children. Comment id doesn't exist."); 
												 jsonresponse.put("response_status", "error");
											 }
											 else
											 {
												 CommentDeleter cd = new CommentDeleter(c, mapper, dynamo_config);
												 cd.start();
												 jsonresponse.put("response_status", "success"); // get fuller version
											 }
										 }
									 }
									 else if (method.equals("getUserByScreenname")) // email, this_access_token, target_email (of user to get) // also an admin method
									 {
										 String target_screenname = request.getParameter("target_screenname");
										 if(target_screenname == null)
										 {
											 jsonresponse.put("message", "Error: target_screenname value was null.");
											 jsonresponse.put("response_status", "error");
										 }
										 else
										 {	 
											 UserItem targetuseritem = mapper.getUserItemFromScreenname(target_screenname);
											 if(targetuseritem == null)
											 {
												 jsonresponse.put("message", "Error: Invalid target screenname.");
												 jsonresponse.put("response_status", "error");
											 }
											 else // at this point, querying user's email and screenname have been found and target screenname exists.
											 {	
												 if(useritem.getScreenname().equals(target_screenname)) // if getting self...
												 {
													 boolean get_email = true;
													 boolean get_this_access_token = true; 
													 boolean get_activity_ids = true; 
													 boolean get_email_preferences = true; 
													 boolean get_notification_count = true;
													 boolean get_seen = true;
													 jsonresponse.put("target_user_jo", targetuseritem.getAsJSONObject(get_email, get_this_access_token, get_activity_ids, get_email_preferences, get_notification_count, get_seen, client, mapper, dynamo_config)); // get stripped-down version
													 jsonresponse.put("self_response", true);
												 }
												 else
												 {
													 boolean get_email = false;
													 boolean get_this_access_token = false; 
													 boolean get_activity_ids = false; 
													 boolean get_email_preferences = false; 
													 boolean get_notification_count = false;
													 boolean get_seen = false;
													 jsonresponse.put("target_user_jo", targetuseritem.getAsJSONObject(get_email, get_this_access_token, get_activity_ids, get_email_preferences, get_notification_count, get_seen, client, mapper, dynamo_config)); // get stripped-down version
													 jsonresponse.put("self_response", false);
												 }
											 }
										 }
									 }
									 else if (method.equals("setProvisionalEmail"))
									 {
										 String provisional_email = request.getParameter("provisional_email");
										 String current = request.getParameter("current_password");
										 if(current == null)
										 {
											 jsonresponse.put("message", "Current password cannot be null.");
											 jsonresponse.put("response_status", "error");
										 }
										 else if(!useritem.isCurrentPassword(current))
										 {
											 jsonresponse.put("message", "Incorrect current password.");
											 jsonresponse.put("response_status", "error");
										 }
										 else if(provisional_email == null)
										 {
											 jsonresponse.put("message", "Method requires a provisional_email value");
											 jsonresponse.put("response_status", "error");
										 }
										 else
										 {
											 EmailValidator ev = EmailValidator.getInstance(false); // local addresses (@localhost, eg) should not be allowed.
											 if(!ev.isValid(provisional_email))
											 {
												 jsonresponse.put("message", "That email is invalid.");
												 jsonresponse.put("response_status", "error");
											 }
											 else
											 {
												 if(provisional_email.endsWith("@words4chrome.com"))
												 {
													 jsonresponse.put("message", "That email is invalid.");
													 jsonresponse.put("response_status", "error");
												 }
												 else
												 {	 
													 UserItem testuseritem = mapper.getUserItemFromEmail(provisional_email);
													 if(testuseritem == null) // good, this email does not already exist in the system
													 {
														 useritem.setProvisionalEmail(provisional_email);
														 String code = UUID.randomUUID().toString().replaceAll("-","").substring(0,5);
														 useritem.setProvisionalEmailConfirmationCode(code);
														 useritem.setProvisionalEmailConfirmationCodeMSFE(System.currentTimeMillis());
														 mapper.save(useritem);
														 useritem.sendEmailConfirmationCode(provisional_email, code);
														 jsonresponse.put("response_status", "success");
														 jsonresponse.put("message", "Confirmation email sent.");
													 }
													 else
													 {
														 jsonresponse.put("message", "That email is already associated with a different account.");
														 jsonresponse.put("response_status", "error");
													 }
												 }
											 }
										 }
									 }
									 else if (method.equals("confirmEmailAddressWithCode"))
									 {
										 String email_to_confirm = request.getParameter("email_to_confirm");
										 String confirmation_code = request.getParameter("confirmation_code");
										 if(confirmation_code == null || email_to_confirm == null )
										 {
											 jsonresponse.put("message", "Method requires an confirmation_code and email_to_confirm values");
											 jsonresponse.put("response_status", "error");
										 }
										 else
										 {	 
											 if(useritem.getProvisionalEmailConfirmationCode() == null || useritem.getProvisionalEmailConfirmationCodeMSFE() == 0L)
											 {
												 jsonresponse.put("message", "There is no provisional email or confirmation code for this user. Please start over and try again.");
												 jsonresponse.put("response_status", "error");
												 useritem.setProvisionalEmail(null);
												 useritem.setProvisionalEmailConfirmationCode(null);
												 useritem.setProvisionalEmailConfirmationCodeMSFE(0L);
												 mapper.save(useritem);
											 }
											 else if(useritem.getProvisionalEmailConfirmationCodeMSFE() + 600000 > System.currentTimeMillis()) // still valid
											 {
												 if(email_to_confirm.equals(useritem.getProvisionalEmail()) && confirmation_code.equals(useritem.getProvisionalEmailConfirmationCode()) 
														 && mapper.getUserItemFromEmail(email_to_confirm) == null) // gotta make sure this email hasn't become used in the meantime
												 {
													 jsonresponse.put("response_status", "success");
													 jsonresponse.put("message", "Email confirmed");
													 useritem.setProvisionalEmail(null);
													 useritem.setProvisionalEmailConfirmationCode(null);
													 useritem.setProvisionalEmailConfirmationCodeMSFE(0L);
													 useritem.setEmailIsConfirmed(true);
													 useritem.setEmail(email_to_confirm);
													 mapper.save(useritem);
												 }
												 else
												 {
													 jsonresponse.put("message", "Email and code did not match or user now exists. Please start over.");
													 jsonresponse.put("response_status", "error");
													 useritem.setProvisionalEmail(null);
													 useritem.setProvisionalEmailConfirmationCode(null);
													 useritem.setProvisionalEmailConfirmationCodeMSFE(0L);
													 mapper.save(useritem);
												 }
											 }
											 else
											 {
												 jsonresponse.put("message", "The provisional code has expired. Please try again.");
												 jsonresponse.put("response_status", "error");
												 useritem.setProvisionalEmail(null);
												 useritem.setProvisionalEmailConfirmationCode(null);
												 useritem.setProvisionalEmailConfirmationCodeMSFE(0L);
												 mapper.save(useritem);
											 }
										 }
									 }
									 else if (method.equals("removeEmail"))
									 {
										 String email_to_remove = request.getParameter("email_to_remove");
										 String current = request.getParameter("current_password");
										 if(!useritem.isCurrentPassword(current))
										 {
											 jsonresponse.put("message", "Wrong password.");
											 jsonresponse.put("response_status", "error");
										 }
										 else if(email_to_remove == null)
										 {
											 jsonresponse.put("message", "Method requires an email_to_remove value");
											 jsonresponse.put("response_status", "error");
										 }
										 else
										 {	 
											 UserItem testuseritem = mapper.getUserItemFromEmail(email_to_remove);
											 if(testuseritem == null) // good, this email does not already exist in the system
											 {
												 jsonresponse.put("message", "That email is not associated with any accounts.");
												 jsonresponse.put("response_status", "error");
											 }
											 else
											 {
												 useritem.setEmail(screenname + "@words4chrome.com");
												 useritem.setEmailIsConfirmed(false);
												 mapper.save(useritem);
												 jsonresponse.put("response_status", "success");
												 jsonresponse.put("message", "Email removed. New value set to " + screenname + "@words4chrome.com");
											 }
										 }
									 }
									 else if (method.equals("changePassword"))
									 {
										 String current = request.getParameter("current_password");
										 String newpass = request.getParameter("new_password");
										 if(!useritem.isCurrentPassword(current))
										 {
											 jsonresponse.put("message", "Incorrect current password.");
											 jsonresponse.put("response_status", "error");
										 }
										 else if(!isValidPassword(newpass))
										 {
											 jsonresponse.put("message", "Invalid new password value.");
											 jsonresponse.put("response_status", "error");
										 }
										 else
										 {
											 SHARight sr = new SHARight();
											 String salt = "";
											 try{
												 salt = sr.getSalt();
												 String secpass = sr.get_SHA_512_SecurePassword(newpass, salt);
												 useritem.setEncryptedPassword(secpass);
												 useritem.setSalt(salt);
												 mapper.save(useritem);
												 jsonresponse.put("response_status", "success"); 
												 jsonresponse.put("message", "Password changed.");
											 }
											 catch(NoSuchAlgorithmException nsae)
											 {
												 jsonresponse.put("response_status", "error");
												 jsonresponse.put("message", "No such encryption algorithm exception.");
											 }	
										 }
									 }
									 else if (method.equals("noteSocialShare")) // email, this_access_token, target_email (of user to get) // also an admin method
									 {
										 String which = request.getParameter("which");
										 if(which == null || which.isEmpty() || (!which.equals("facebook") && !which.equals("twitter")))
										 {
											 System.out.println("noteSocialShare: invalid which");
											 jsonresponse.put("response_status", "error");
											 jsonresponse.put("message", "Invalid which value.");
										 }
										 else if(useritem.getEmailIsConfirmed() == false || useritem.getNumCommentsAuthored() == 0)
										 {
											 System.out.println("noteSocialShare: user ineligible");
											 jsonresponse.put("response_status", "error");
											 jsonresponse.put("message", "User is not yet eligible for giveaways. Can't note additional entries for social sharing.");
										 }
										 else if(which.equals("facebook"))
										 {
											 System.out.println("noteSocialShare: seting shared to fb=true for screenname=" + useritem.getScreenname());
											 useritem.setSharedToFacebook(true);
											 mapper.save(useritem);
											 jsonresponse.put("response_status", "success");
											 jsonresponse.put("which", which);
										 }
										 else if(which.equals("twitter"))
										 {
											 System.out.println("noteSocialShare: seting shared to twitter=true for screenname=" + useritem.getScreenname());
											 useritem.setSharedToTwitter(true);
											 mapper.save(useritem);
											 jsonresponse.put("response_status", "success");
											 jsonresponse.put("which", which);
										 }
									 }
									 else if (method.equals("setUserPreference")) // email, this_access_token, target_email (of user to get) // also an admin method
									 {
										 //System.out.println("Endpoint setUserPreference() begin: which=" + which + " and value=" + value);
										 String which = request.getParameter("which");
										 String value = request.getParameter("value");
										 if(which == null || value == null)
										 {
											 jsonresponse.put("message", "Invalid parameters.");
											 jsonresponse.put("response_status", "error");
										 }
										 else
										 {	 
											 jsonresponse.put("response_status", "success"); // default to success, then overwrite with error if necessary
											 if(which.equals("onreply") || which.equals("onlike") 
													 || which.equals("ondislike") || which.equals("onmention") 
													 	|| which.equals("onfollowcomment") || which.equals("promos"))
											 {
												 if(value.equals("button") || value.equals("do nothing"))
												 {
													 if(which.equals("onreply"))
														 useritem.setOnReply(value);
													 else if(which.equals("onlike"))
														 useritem.setOnLike(value);
													 else if(which.equals("ondislike"))
														 useritem.setOnDislike(value);
													 else if(which.equals("onmention"))
														 useritem.setOnMention(value);
													 else if(which.equals("onfollowcomment"))
														 useritem.setOnFollowComment(value);
													 else if(which.equals("promos"))
														 useritem.setPromos(value);
													 mapper.save(useritem);	
													 jsonresponse.put("response_status", "success"); 
												 }
												 else
												 {
													 jsonresponse.put("message", "Invalid value.");
													 jsonresponse.put("response_status", "error");
												 }
											 }
											 else if(which.equals("footermessages"))
											 {
												 if(value.equals("hide"))
													 useritem.setShowFooterMessages(false);
												 else
													 useritem.setShowFooterMessages(true);
												 mapper.save(useritem);
												 jsonresponse.put("response_status", "success");
											 }
											 else if(which.equals("overlay_size"))
											 {
												 if(value.equals("medium"))
													 useritem.setOverlaySize(450);
												 else if(value.equals("wide"))
													 useritem.setOverlaySize(600);
												 else // this is an error, default to 450
													 useritem.setOverlaySize(450);
												 mapper.save(useritem);
												 jsonresponse.put("response_status", "success"); 
											 }
											 else if(which.equals("picture")) // NOTE: THIS SETS WHICH_PICTURE TO "avatar_icon" AUTOMATICALLY (prevents 2 calls)
											 {
												 if(!Global.isValidPicture(value))
												 {
													 jsonresponse.put("message", "Invalid value.");
													 jsonresponse.put("response_status", "error");
												 }
												 else
												 {	 
													 useritem.setPicture(value);
													 mapper.save(useritem);
													 jsonresponse.put("response_status", "success"); 
												 }
											 }
											 else if(which.equals("screenname"))
											 {
												 String password = request.getParameter("password");
												 if(password == null)
												 {
													 jsonresponse.put("message", "Password value was null.");
													 jsonresponse.put("response_status", "error");
												 }
												 else
												 {	 
													 String current_screenname = useritem.getScreenname();
													 if(!isValidScreenname(value))
													 {	jsonresponse.put("response_status", "error"); jsonresponse.put("message", "Screenname is invalid. Must be 3-15 letters & digits, starting with a letter.");		}
													 else if(value.equals(current_screenname)) // lowercase to lowercase check
													 {
														 jsonresponse.put("response_status", "error"); jsonresponse.put("message", "Screenname must be different than your old one.");	
													 }
													 else if(!useritem.isCurrentPassword(password))
													 {
														 jsonresponse.put("response_status", "error"); jsonresponse.put("message", "Invalid password.");	
													 }
													 else
													 {
														 UserItem u = mapper.getUserItemFromScreenname(value);
														 boolean screenname_is_available =  (u == null) ? true : false;
														 if(screenname_is_available)
														 {
															 if(useritem.getEmail().endsWith("@words4chrome.com")) // if this is a words4chrome address, it has to stay the same as the screenname(literal)
																 useritem.setEmail(value + "@words4chrome.com");
															 useritem.setScreenname(value);
															 mapper.save(useritem); 
															 jsonresponse.put("response_status", "success"); 
														 }
														 else
														 {
															 jsonresponse.put("response_status", "error"); jsonresponse.put("message", "That screenname is not available.");	
														 }
													 }
												 }
											 }
											 else
											 {
												 jsonresponse.put("message", "Invalid which value.");
												 jsonresponse.put("response_status", "error");
											 }
										 }
									 }
									 else if (method.equals("getMyComments"))
									 {
										 //System.out.println("Endpoint getMyComments() begin);
										 TreeSet<CommentItem> comments_authored = useritem.getCommentsAuthored(0, mapper, dynamo_config); // 0 == get all
										 TreeSet<String> comment_ids = new TreeSet<String>();
										 if(comments_authored == null)
										 {
											 jsonresponse.put("response_status", "success");
										 }
										 else
										 {
											 Iterator<CommentItem> it = comments_authored.iterator();
											 CommentItem currentitem = null;
											 while(it.hasNext())
											 {
												 currentitem = it.next();
												 comment_ids.add(currentitem.getId());
											 }
											 jsonresponse.put("comments_ja", new JSONArray(comment_ids));
											 jsonresponse.put("response_status", "success");
										 }
										 //System.out.println("Endpoint getMyComments() end);
									 }
									 else if (method.equals("resetActivityCount"))
									 {
										 //System.out.println("Endpoint resetActivityCount() begin);
										 useritem.setNotificationCount(0);
										 mapper.save(useritem);
										 jsonresponse.put("message", "Activity count successfully reset."); 
										 jsonresponse.put("response_status", "success");
										//System.out.println("Endpoint resetActivityCount() end);
									 }
									
									 else if (method.equals("removeItemFromActivityIds"))
									 {
										 //System.out.println("Endpoint.removeItemFromActivityIds() begin");
										 Set<String> activityset = useritem.getActivityIds();
										 if(activityset != null)
										 {
											 activityset.remove(request.getParameter("id"));
											 if(activityset.isEmpty())
												 activityset = null;
											 useritem.setActivityIds(activityset);
											 mapper.save(useritem);
										 }
										 // else activity set was already null, no need to return an error.
										 jsonresponse.put("response_status", "success");
										 //System.out.println("Endpoint.removeItemFromActivityIds() end");
									 }
									 else if (method.equals("haveILikedThisComment"))
									 {
										 //System.out.println("Endpoint.haveILikedThisComment() begin");
										 String id = request.getParameter("id");
										 if(id == null)
										 {
											 jsonresponse.put("message", "Invalid comment id.");
											 jsonresponse.put("response_status", "error");
										 }	
										 else
										 {	 
											 boolean user_already_liked = useritem.hasLikedComment(id, mapper, dynamo_config);
											 jsonresponse.put("response_status", "success");
											 if(user_already_liked)
												 jsonresponse.put("response_value", true);
											 else
												 jsonresponse.put("response_value", false);
										 }
										 //System.out.println("Endpoint.haveILikedThisComment() end");
									 }
									 else if (method.equals("haveIDislikedThisComment"))
									 {
										 //System.out.println("Endpoint.haveIDislikedThisComment() begin");
										 String id = request.getParameter("id");
										 if(id == null)
										 {
											 jsonresponse.put("message", "Invalid comment id.");
											 jsonresponse.put("response_status", "error");
										 }	
										 else
										 {	 
											 boolean user_already_liked = useritem.hasDislikedComment(id, mapper, dynamo_config);
											 jsonresponse.put("response_status", "success");
											 if(user_already_liked)
												 jsonresponse.put("response_value", true);
											 else
												 jsonresponse.put("response_value", false);
										 }
										 //System.out.println("Endpoint.haveIDislikedThisComment() end");
									 } 
									 else if (method.equals("savePicture"))
									 {
										 //System.out.println("Endpoint.savePicture() begin");
										 String picture = request.getParameter("picture");
										 if(!Global.isValidPicture(picture))
										 {
											 jsonresponse.put("message", "Invalid picture url.");
											 jsonresponse.put("response_status", "error");
										 }	
										 else
										 {	 
											useritem.setPicture(picture);
											mapper.save(useritem);
											jsonresponse.put("response_status", "success");
											jsonresponse.put("picture", useritem.getPicture());
										 }
										 //System.out.println("Endpoint.savePicture() end");
									 }
								 }
								 else // user had an screenname and this_access_token, but they were not valid. Let the frontend know to get rid of them
								 {
									 jsonresponse.put("response_status", "error");
									 jsonresponse.put("message", "screenname + access token present, but not valid. Please try again.");
									 jsonresponse.put("error_code", "0000");
								 }
							 }
							 else // couldn't get useritem from provided screenname
							 {
								 jsonresponse.put("response_status", "error");
								 jsonresponse.put("message", "No user was found for that screenname. Please try again.");
								 jsonresponse.put("error_code", "0000");
							 }
					 	}
					 	else // either screenname or tat was null, but not both
					 	{
					 		jsonresponse.put("response_status", "error");
					 		jsonresponse.put("message", "screenname or access token was null. Please try again.");
					 		jsonresponse.put("error_code", "0000");
					 	}
					 }	
					 else // email and tat were both null
					 {
						 jsonresponse.put("response_status", "error");
						 jsonresponse.put("message", "You must be logged in to do that.");
					 }
				 }
				 else
				 {
					 jsonresponse.put("response_status", "error");
					 jsonresponse.put("message", "Unsupported method. method=" + method);
				 }
			}
			long timestamp_at_exit = System.currentTimeMillis();
			long elapsed = timestamp_at_exit - timestamp_at_entry;
			jsonresponse.put("elapsed", elapsed);
			if(method != null)
				jsonresponse.put("method", method);
			if(devel == true)
				System.out.println("response=" + jsonresponse);	// respond with object, success response, or error 
			out.println(jsonresponse);
		}
		catch(JSONException jsone)
		{
			out.println("{ \"response_status\": \"error\", \"message\": \"JSONException caught in Endpoint GET\"}");
			System.err.println("endpoint: JSONException thrown in large try block. " + jsone.getMessage());
		}		
		return; 	
	}
	
	// post getAuthToken version, copied as closely as possible from the Facebook flow.
	
	private JSONObject loginWithGoogleOrShowRegistration(String google_access_token)
	{
		//System.out.println("Logging in with google access token=" + google_access_token);
		JSONObject jsonresponse = new JSONObject();
		JSONObject profile_info_jo = new JSONObject();
		HttpClient httpclient = new DefaultHttpClient();
		try{
			try {
				HttpGet httpget = new HttpGet("https://www.googleapis.com/plus/v1/people/me?access_token=" + google_access_token);
				 ResponseHandler<String> responseHandler = new BasicResponseHandler();
				 String responseBody = httpclient.execute(httpget, responseHandler);
				 //System.out.println(responseBody);
				 profile_info_jo = new JSONObject(responseBody);
			 } 
			 catch(ClientProtocolException cpe)  
			 {	
				 System.err.println("ClientProtocolException: " + cpe.getMessage() + " " + cpe.getCause() + " " + cpe.getLocalizedMessage()); 
				 cpe.printStackTrace();
				 // if we've gotten here, there's a near-certain chance the user's token is no longer valid. Need to return a "delete cookies" signal.
				 jsonresponse.put("response_status", "error");
				 jsonresponse.put("message", "Your Google token is no longer valid. Please log in again.");
				 jsonresponse.put("error_code", "0000");
				 jsonresponse.put("login_type", "google");
				 return jsonresponse;
			 }
			 catch(IOException ioe) { System.err.println("IOException: " + ioe.getMessage());  }
		}
		catch(JSONException je) { System.err.println("JSONException: " + je.getMessage());	 }
		
		 try{
			// We've got this person's profile. First step is to retrieve email value and see if the user already exists.
			 String email = null;
			 boolean google_profile_contained_valid_email = false;
			 if(profile_info_jo.has("emails"))
			 {
				 JSONArray emails_ja = profile_info_jo.getJSONArray("emails");
				 JSONObject current_email_jo = null;
				 for(int x = 0; x < emails_ja.length(); x++)
				 {
					 current_email_jo = emails_ja.getJSONObject(x);
					 if(current_email_jo.has("type") && current_email_jo.get("type").equals("account") && current_email_jo.has("value"))
					 {
						 email = current_email_jo.getString("value");
						 google_profile_contained_valid_email = true;
						 break;
					 }
				 }
			 }
			 else
			 {
				 System.err.println("Endpoint method=loginWithGoogleOrShowRegistration: Got access_token, but google profile did not contain \"emails\" array.");
				 jsonresponse.put("response_status", "error");
				 jsonresponse.put("message", "Got access_token, but google profile did not contain \"emails\" array.");
				 jsonresponse.put("login_type", "google");
			 }

			 if(google_profile_contained_valid_email)
			 {
				 UserItem useritem = mapper.getUserItemFromEmail(email);
				 if(useritem != null)
				 {
					 String uuid_str = UUID.randomUUID().toString().replaceAll("-","");
					 Calendar cal = Calendar.getInstance();
					 long now = cal.getTimeInMillis();
					 cal.add(Calendar.YEAR, 1);
					 long future = cal.getTimeInMillis();
					 
					 useritem.setThisAccessToken(uuid_str);
					 useritem.setThisAccessTokenExpires(future);
					 useritem.setLastLoginType("google");
					 useritem.setSeen(now);
					 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
					 sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
					 useritem.setSeenHumanReadable(sdf.format(cal.getTimeInMillis()));
					 mapper.save(useritem);
					 
					 //System.out.println("Endpoint.loginWithGoogleOrShowRegistration() user already registered, logging in");
					 jsonresponse.put("response_status", "success");
					 jsonresponse.put("this_access_token", uuid_str);
					 jsonresponse.put("screenname", useritem.getScreenname());
					 jsonresponse.put("show_registration", "false");
					 jsonresponse.put("google_access_token", google_access_token);
					 jsonresponse.put("login_type", "google");
				 }
				 else // this is actually a success condition. Access token works, now show registration with four pieces of info, gleaned from 
				 {	  // google profile object: 1. profile pic url 2. access_token 3. email (4. reiterate login_type)
					 //System.out.println("Endpoint.loginWithGoogleOrShowRegistration(): Email not found, show user registration.");
					 jsonresponse.put("response_status", "success");
					 jsonresponse.put("show_registration", "true");
					 jsonresponse.put("email", email);
					 
					 String image_url = profile_info_jo.getJSONObject("image").getString("url");
					 image_url = image_url.replaceAll("sz=50", "sz=128");
					 jsonresponse.put("picture", image_url);
					 jsonresponse.put("google_access_token", google_access_token);
					 jsonresponse.put("login_type", "google");
				 }
			 }
			 else
			 {
				 System.err.println("Endpoint method=loginWithGoogleOrShowRegistration: Got access_token, and profile contained emails array, but none of type \"account\".");
				 jsonresponse.put("response_status", "error");
				 jsonresponse.put("message", "Got access_token, and profile contained emails array, but none of type \"account\".");
				 jsonresponse.put("login_type", "google");
			 } 
		 }	
		 catch (JSONException e) {
			 e.printStackTrace();
		 }
		 return jsonresponse;
	}
	
	private JSONObject loginWithFacebookOrShowRegistration(String facebook_access_token)
	{
		//System.out.println("Logging in with facebook access token=" + facebook_access_token);
		JSONObject jsonresponse = new JSONObject();
		JSONObject profile_info_jo = new JSONObject();
		HttpClient httpclient = new DefaultHttpClient();
		try{
			try {
				//System.out.println("making request to facebook: https://graph.facebook.com/me?access_token=" + facebook_access_token);
				 HttpGet httpget = new HttpGet("https://graph.facebook.com/me?access_token=" + facebook_access_token);
				 ResponseHandler<String> responseHandler = new BasicResponseHandler();
				 String responseBody = httpclient.execute(httpget, responseHandler);
				 System.out.println(responseBody);
				 profile_info_jo = new JSONObject(responseBody);
			 } 
			 catch(ClientProtocolException cpe)  
			 {	
				 System.err.println("ClientProtocolException: " + cpe.getMessage() + " " + cpe.getCause() + " " + cpe.getLocalizedMessage()); 
				 cpe.printStackTrace();
				 // if we've gotten here, there's a near-certain chance the user's token is no longer valid. Need to return a "delete cookies" signal.
				 jsonresponse.put("response_status", "error");
				 jsonresponse.put("message", "Your Facebook token is no longer valid. Please log in again.");
				 jsonresponse.put("error_code", "0000");
				 jsonresponse.put("login_type", "facebook");
				 return jsonresponse;
			 }
			 catch(IOException ioe) { System.err.println("IOException: " + ioe.getMessage());  }
		}
		catch(JSONException je) { System.err.println("JSONException: " + je.getMessage());	 }
		
		 try{
			// We've got this person's profile. First step is to retrieve email value and see if the user already exists.
			 if(profile_info_jo.has("email"))
			 {
				 String email = profile_info_jo.getString("email");
				 UserItem useritem = mapper.getUserItemFromEmail(email);
				 if(useritem != null)
				 {
						 // This happens when the user is logging in with a code, but is already registered. (with both email and screenname)
					 String uuid_str = UUID.randomUUID().toString().replaceAll("-","");
					 Calendar cal = Calendar.getInstance();
					 long now = cal.getTimeInMillis();
					 cal.add(Calendar.YEAR, 1);
					 long future = cal.getTimeInMillis();
					 useritem.setThisAccessToken(uuid_str);
					 useritem.setThisAccessTokenExpires(future);
					 useritem.setLastLoginType("facebook");
					 useritem.setSeen(now);
					 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
					 sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
					 useritem.setSeenHumanReadable(sdf.format(cal.getTimeInMillis()));
					 mapper.save(useritem);
					 //System.out.println("Endpoint.loginWithFacebookOrShowRegistration() user already registered, logging in");
					 jsonresponse.put("response_status", "success");
					 jsonresponse.put("this_access_token", uuid_str);
					 jsonresponse.put("screenname", useritem.getScreenname());
					 jsonresponse.put("show_registration", "false");
					 jsonresponse.put("facebook_access_token", facebook_access_token);
					 jsonresponse.put("login_type", "facebook");
				 }
				 else // this is actually a success condition. Access token works, now show registration with four pieces of info, gleaned from 
				 {	  // google profile object: 1. name 2. profile pic url 3. access_token 4. email (5. reiterate login_type)
					 //System.out.println("Endpoint.loginWithFacebookOrShowRegistration(): Email not found, show user registration.");
					 if(profile_info_jo.getBoolean("verified") == true)
					 {		
						 jsonresponse.put("response_status", "success");
						 jsonresponse.put("show_registration", "true");
						 jsonresponse.put("email", email);
						 String image_url = "http://graph.facebook.com/" + profile_info_jo.get("id") + "/picture?width=128&height=128";
						 jsonresponse.put("picture", image_url);
						// jsonresponse.put("name",profile_info_jo.getString("first_name"));
						 jsonresponse.put("facebook_access_token", facebook_access_token);
						 jsonresponse.put("login_type", "facebook");
					 }
					 else
					 {
						 //System.out.println("Endpoint.loginWithFacebookOrShowRegistration(): Facebook email not verified.");
						 jsonresponse.put("response_status", "error");
						 jsonresponse.put("message", "Sorry, the email associated with your Facebook account is not verified. Please verify it with Facebook and try again.");
						 jsonresponse.put("login_type", "facebook");
					 }
				 }	
			 }
			 else
			 {
				 jsonresponse.put("response_status", "error");
				 jsonresponse.put("message", "Sorry, your Facebook profile doesn't contain a verified email address.");
				 jsonresponse.put("login_type", "facebook");
			 }
		 }	
		 catch (JSONException e) {
			 e.printStackTrace();
		 }
		 return jsonresponse;
	}
	
	private JSONObject getGooglePicture(String google_access_token)
	{
		JSONObject jsonresponse = new JSONObject();
		JSONObject profile_info_jo = new JSONObject();
		HttpClient httpclient = new DefaultHttpClient();
		try{
			try {
				HttpGet httpget = new HttpGet("https://www.googleapis.com/plus/v1/people/me?access_token=" + google_access_token);
				 ResponseHandler<String> responseHandler = new BasicResponseHandler();
				 String responseBody = httpclient.execute(httpget, responseHandler);
				 //System.out.println(responseBody);
				 profile_info_jo = new JSONObject(responseBody);
			 } 
			 catch(ClientProtocolException cpe)  
			 {	
				 System.err.println("ClientProtocolException: " + cpe.getMessage() + " " + cpe.getCause() + " " + cpe.getLocalizedMessage()); 
				 cpe.printStackTrace();
				 // if we've gotten here, there's a near-certain chance the user's token is no longer valid. Need to return a "delete cookies" signal.
				 jsonresponse.put("response_status", "error");
				 jsonresponse.put("message", "Your Google token is no longer valid. Please log in again.");
				 jsonresponse.put("error_code", "0000");
				 jsonresponse.put("login_type", "google");
				 return jsonresponse;
			 }
			 catch(IOException ioe) { System.err.println("IOException: " + ioe.getMessage());  }
		}
		catch(JSONException je) { System.err.println("JSONException: " + je.getMessage());	 }
		
		 try{
			 String image_url = profile_info_jo.getJSONObject("image").getString("url");
			 image_url = image_url.replaceAll("sz=50", "sz=128");
			 jsonresponse.put("picture", image_url);
			 jsonresponse.put("response_status", "success");
		 }	
		 catch (JSONException e) {
			 e.printStackTrace();
		 }
		 return jsonresponse;
	}
	
	private JSONObject getFacebookPicture(String facebook_access_token)
	{
		JSONObject jsonresponse = new JSONObject();
		JSONObject profile_info_jo = new JSONObject();
		HttpClient httpclient = new DefaultHttpClient();
		try{
			try {
				 HttpGet httpget = new HttpGet("https://graph.facebook.com/me?access_token=" + facebook_access_token);
				 ResponseHandler<String> responseHandler = new BasicResponseHandler();
				 String responseBody = httpclient.execute(httpget, responseHandler);
				 //System.out.println(responseBody);
				 profile_info_jo = new JSONObject(responseBody);
			 } 
			 catch(ClientProtocolException cpe)  
			 {	
				 System.err.println("ClientProtocolException: " + cpe.getMessage() + " " + cpe.getCause() + " " + cpe.getLocalizedMessage()); 
				 cpe.printStackTrace();
				 // if we've gotten here, there's a near-certain chance the user's token is no longer valid. Need to return a "delete cookies" signal.
				 jsonresponse.put("response_status", "error");
				 jsonresponse.put("message", "Your Facebook token is no longer valid. Please log in again.");
				 jsonresponse.put("error_code", "0000");
				 jsonresponse.put("login_type", "facebook");
				 return jsonresponse;
			 }
			 catch(IOException ioe) { System.err.println("IOException: " + ioe.getMessage());  }
		}
		catch(JSONException je) { System.err.println("JSONException: " + je.getMessage());	 }
		
		try{
			String image_url = "http://graph.facebook.com/" + profile_info_jo.get("id") + "/picture?width=128&height=128";
			jsonresponse.put("picture", image_url);
			jsonresponse.put("response_status", "success");
		}	
		catch (JSONException e) {
			e.printStackTrace();
		}
		return jsonresponse;
	}
	
	public static void main(String [] args)
	{
		//Endpoint endpoint = new Endpoint();
		
	}
}
