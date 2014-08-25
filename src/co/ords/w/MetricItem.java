package co.ords.w;


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

@DynamoDBTable(tableName="words_metrics")
public class MetricItem implements java.lang.Comparable<MetricItem> {

	// part of the item itself 
	private String id;
	private long msfe;
	private String timestamp_hr;
	private long processing_time_in_ms;
	
	// user activity
	private long registered_users;
	private long active30;
	private long active7;
	private long active1;
	private long commented30;
	private long commented7;
	private long commented1;
	private long authoredlike30;
	private long authoredlike7;
	private long authoredlike1;
	private long authoreddislike30;
	private long authoreddislike7;
	private long authoreddislike1;
	private long authoredhpqsplike30;
	private long authoredhpqsplike7;
	private long authoredhpqsplike1;
	private long authoredhostnamelike30;
	private long authoredhostnamelike7;
	private long authoredhostnamelike1;
	private long registered_commented_confirmed;

	// login types
	private long google_as_last_login_type;
	private long facebook_as_last_login_type;
	private long native_as_last_login_type;
	
	// threadviews info
	private long threadretrievals;
	private long threadviews;
	private long threadviews30;
	private long threadviews7;
	private long threadviews1;
	private long emptythreadviews30;
	private long emptythreadviews7;
	private long emptythreadviews1;
	private long loggedinthreadviews30;
	private long loggedinthreadviews7;
	private long loggedinthreadviews1;
	
	private long separatedthreadviews30;
	private long separatedthreadviews7;
	private long separatedthreadviews1;
	private long sqspthreadviews30;
	private long sqspthreadviews7;
	private long sqspthreadviews1;
	
	// Two reasons to track something:
	// 1. If the overall metric is just inherently important -- Ex: active users, contributing users, etc
	// 2. To gage whether or not to continue doing X -- Ex: footer/about promo sources. gp/tumblr/gmail platforms

	// When NOT to track something:
	// 1. If the metric is not that important -- Ex: male/female
	// 2. If the metric can be estimated from the web site's Google Analytics -- Ex: male/female, geography, etc
	// 3. If we're going to continue doing something, regardless of the results -- Ex: mobile-to-desktop reminders
	
	@DynamoDBHashKey(attributeName="id") 
	public String getId() {return id; }
	public void setId(String id) { this.id = id; }
	
	@DynamoDBAttribute(attributeName="msfe")  
	public long getMSFE() {return msfe; }
	public void setMSFE(long msfe) { this.msfe = msfe; }
	
	@DynamoDBAttribute(attributeName="timestamp_hr")  
	public String getTimestampHumanReadable() {return timestamp_hr; }  // note this should not be used. Always format and return the msfe value instead.
	public void setTimestampHumanReadable(String timestamp_hr) { this.timestamp_hr = timestamp_hr; }
	
	@DynamoDBAttribute(attributeName="registered_users")  
	public long getRegisteredUsers() {return registered_users; }
	public void setRegisteredUsers(long registered_users) { this.registered_users = registered_users; }
	
	@DynamoDBAttribute(attributeName="registered_commented_confirmed")  
	public long getRegisteredCommentedConfirmed() {return registered_commented_confirmed; }
	public void setRegisteredCommentedConfirmed(long registered_commented_confirmed) { this.registered_commented_confirmed = registered_commented_confirmed; }
	
	@DynamoDBAttribute(attributeName="active30")  
	public long getActive30() {return active30; }
	public void setActive30(long active30) { this.active30 = active30; }
	
	@DynamoDBAttribute(attributeName="active7")  
	public long getActive7() {return active7; }
	public void setActive7(long active7) { this.active7 = active7; }
	
	@DynamoDBAttribute(attributeName="active1")  
	public long getActive1() {return active1; }
	public void setActive1(long active1) { this.active1 = active1; }
	
	@DynamoDBAttribute(attributeName="commented30")  
	public long getCommented30() {return commented30; }
	public void setCommented30(long commented30) { this.commented30 = commented30; }
	
	@DynamoDBAttribute(attributeName="commented7")  
	public long getCommented7() {return commented7; }
	public void setCommented7(long commented7) { this.commented7 = commented7; }
	
	@DynamoDBAttribute(attributeName="commented1")  
	public long getCommented1() {return commented1; }
	public void setCommented1(long commented1) { this.commented1 = commented1; }
	
	@DynamoDBAttribute(attributeName="authoredlike30")  
	public long getAuthoredLike30() {return authoredlike30; }
	public void setAuthoredLike30(long authoredlike30) { this.authoredlike30 = authoredlike30; }
	
	@DynamoDBAttribute(attributeName="authoredlike7")  
	public long getAuthoredLike7() {return authoredlike7; }
	public void setAuthoredLike7(long authoredlike7) { this.authoredlike7 = authoredlike7; }
	
	@DynamoDBAttribute(attributeName="authoredlike1")  
	public long getAuthoredLike1() {return authoredlike1; }
	public void setAuthoredLike1(long authoredlike1) { this.authoredlike1 = authoredlike1; }
	
	@DynamoDBAttribute(attributeName="authoreddislike30")  
	public long getAuthoredDislike30() {return authoreddislike30; }
	public void setAuthoredDislike30(long authoreddislike30) { this.authoreddislike30 = authoreddislike30; }
	
	@DynamoDBAttribute(attributeName="authoreddislike7")  
	public long getAuthoredDislike7() {return authoreddislike7; }
	public void setAuthoredDislike7(long authoreddislike7) { this.authoreddislike7 = authoreddislike7; }
	
	@DynamoDBAttribute(attributeName="authoreddislike1")  
	public long getAuthoredDislike1() {return authoreddislike1; }
	public void setAuthoredDislike1(long authoreddislike1) { this.authoreddislike1 = authoreddislike1; }
	
	@DynamoDBAttribute(attributeName="authoredhpqsplike30")  
	public long getAuthoredHPQSPLike30() {return authoredhpqsplike30; }
	public void setAuthoredHPQSPLike30(long authoredhpqsplike30) { this.authoredhpqsplike30 = authoredhpqsplike30; }
	
	@DynamoDBAttribute(attributeName="authoredhpqsplike7")  
	public long getAuthoredHPQSPLike7() {return authoredhpqsplike7; }
	public void setAuthoredHPQSPLike7(long authoredhpqsplike7) { this.authoredhpqsplike7 = authoredhpqsplike7; }
	
	@DynamoDBAttribute(attributeName="authoredhpqsplike1")  
	public long getAuthoredHPQSPLike1() {return authoredhpqsplike1; }
	public void setAuthoredHPQSPLike1(long authoredhpqsplike1) { this.authoredhpqsplike1 = authoredhpqsplike1; }
	
	@DynamoDBAttribute(attributeName="authoredhostnamelike30")  
	public long getAuthoredHostnameLike30() {return authoredhostnamelike30; }
	public void setAuthoredHostnameLike30(long authoredhostnamelike30) { this.authoredhostnamelike30 = authoredhostnamelike30; }
	
	@DynamoDBAttribute(attributeName="authoredhostnamelike7")  
	public long getAuthoredHostnameLike7() {return authoredhostnamelike7; }
	public void setAuthoredHostnameLike7(long authoredhostnamelike7) { this.authoredhostnamelike7 = authoredhostnamelike7; }
	
	@DynamoDBAttribute(attributeName="authoredhostnamelike1")  
	public long getAuthoredHostnameLike1() {return authoredhostnamelike1; }
	public void setAuthoredHostnameLike1(long authoredhostnamelike1) { this.authoredhostnamelike1 = authoredhostnamelike1; }
	
	@DynamoDBAttribute(attributeName="google_as_last_login_type")  
	public long getGoogleLast() {return google_as_last_login_type; }
	public void setGoogleLast(long google_as_last_login_type) { this.google_as_last_login_type = google_as_last_login_type; }
	
	@DynamoDBAttribute(attributeName="facebook_as_last_login_type")  
	public long getFacebookLast() {return facebook_as_last_login_type; }
	public void setFacebookLast(long facebook_as_last_login_type) { this.facebook_as_last_login_type = facebook_as_last_login_type; }
	
	@DynamoDBAttribute(attributeName="native_as_last_login_type")  
	public long getNativeLast() {return native_as_last_login_type; }
	public void setNativeLast(long native_as_last_login_type) { this.native_as_last_login_type = native_as_last_login_type; }
	
	@DynamoDBAttribute(attributeName="threadretrievals")  
	public long getThreadRetrievals() {return threadretrievals; }
	public void setThreadRetrievals(long threadretrievals) { this.threadretrievals = threadretrievals; }
	
	@DynamoDBAttribute(attributeName="threadviews")  
	public long getThreadViews() {return threadviews; }
	public void setThreadViews(long threadviews) { this.threadviews = threadviews; }
	
	@DynamoDBAttribute(attributeName="threadviews30")  
	public long getThreadViews30() {return threadviews30; }
	public void setThreadViews30(long threadviews30) { this.threadviews30 = threadviews30; }
	
	@DynamoDBAttribute(attributeName="threadviews7")  
	public long getThreadViews7() {return threadviews7; }
	public void setThreadViews7(long threadviews7) { this.threadviews7 = threadviews7; }
	
	@DynamoDBAttribute(attributeName="threadviews1")  
	public long getThreadViews1() {return threadviews1; }
	public void setThreadViews1(long threadviews1) { this.threadviews1 = threadviews1; }
	
	@DynamoDBAttribute(attributeName="separatedthreadviews30")  
	public long getSeparatedThreadViews30() {return separatedthreadviews30; }
	public void setSeparatedThreadViews30(long separatedthreadviews30) { this.separatedthreadviews30 = separatedthreadviews30; }
	
	@DynamoDBAttribute(attributeName="separatedthreadviews7")  
	public long getSeparatedThreadViews7() {return separatedthreadviews7; }
	public void setSeparatedThreadViews7(long separatedthreadviews7) { this.separatedthreadviews7 = separatedthreadviews7; }
	
	@DynamoDBAttribute(attributeName="separatedthreadviews1")  
	public long getSeparatedThreadViews1() {return separatedthreadviews1; }
	public void setSeparatedThreadViews1(long separatedthreadviews1) { this.separatedthreadviews1 = separatedthreadviews1; }
	
	@DynamoDBAttribute(attributeName="sqspthreadviews30")  
	public long getSQSPThreadViews30() {return sqspthreadviews30; }
	public void setSQSPThreadViews30(long sqspthreadviews30) { this.sqspthreadviews30 = sqspthreadviews30; }
	
	@DynamoDBAttribute(attributeName="sqspthreadviews7")  
	public long getSQSPThreadViews7() {return sqspthreadviews7; }
	public void setSQSPThreadViews7(long sqspthreadviews7) { this.sqspthreadviews7 = sqspthreadviews7; }
	
	@DynamoDBAttribute(attributeName="sqspthreadviews1")  
	public long getSQSPThreadViews1() {return sqspthreadviews1; }
	public void setSQSPThreadViews1(long sqspthreadviews1) { this.sqspthreadviews1 = sqspthreadviews1; }
	
	@DynamoDBAttribute(attributeName="loggedinthreadviews30")  
	public long getLoggedInThreadViews30() {return loggedinthreadviews30; }
	public void setLoggedInThreadViews30(long loggedinthreadviews30) { this.loggedinthreadviews30 = loggedinthreadviews30; }
	
	@DynamoDBAttribute(attributeName="loggedinthreadviews7")  
	public long getLoggedInThreadViews7() {return loggedinthreadviews7; }
	public void setLoggedInThreadViews7(long loggedinthreadviews7) { this.loggedinthreadviews7 = loggedinthreadviews7; }
	
	@DynamoDBAttribute(attributeName="loggedinthreadviews1")  
	public long getLoggedInThreadViews1() {return loggedinthreadviews1; }
	public void setLoggedInThreadViews1(long loggedinthreadviews1) { this.loggedinthreadviews1 = loggedinthreadviews1; }
	
	@DynamoDBAttribute(attributeName="emptythreadviews30")  
	public long getEmptyThreadViews30() {return emptythreadviews30; }
	public void setEmptyThreadViews30(long emptythreadviews30) { this.emptythreadviews30 = emptythreadviews30; }
	
	@DynamoDBAttribute(attributeName="emptythreadviews7")  
	public long getEmptyThreadViews7() {return emptythreadviews7; }
	public void setEmptyThreadViews7(long emptythreadviews7) { this.emptythreadviews7 = emptythreadviews7; }
	
	@DynamoDBAttribute(attributeName="emptythreadviews1")  
	public long getEmptyThreadViews1() {return emptythreadviews1; }
	public void setEmptyThreadViews1(long emptythreadviews1) { this.emptythreadviews1 = emptythreadviews1; }
	
	@DynamoDBIgnore
	public void setImpressions(JSONObject inc_jo, AmazonDynamoDBClient client)
	{
		//System.out.println("MetricItem.setImpressions(): entering");
		try
		{
			Map<String, AttributeValueUpdate> updateItems = new HashMap<String, AttributeValueUpdate>();

			HashMap<String, AttributeValue> key = new HashMap<String, AttributeValue>();
			key.put("id", new AttributeValue().withS(getId()));

			@SuppressWarnings("unchecked")
			Iterator<String> iterator = inc_jo.keys();
			String tempkey = "";
			long templong = 0L;
			while(iterator.hasNext())
			{
				
				tempkey = iterator.next();
				templong = inc_jo.getLong(tempkey);
				//System.out.println("MetricItem.setImpressions(): looping key=" + tempkey + " long=" + templong);
				
				updateItems.put(tempkey, 
						new AttributeValueUpdate()
							.withAction(AttributeAction.PUT)
						    .withValue(new AttributeValue().withN((new Long(templong).toString()))));
			}
			            
			UpdateItemRequest updateItemRequest = new UpdateItemRequest()
			  .withTableName("words_metrics")
			  .withKey(key).withReturnValues(ReturnValue.UPDATED_NEW)
			  .withAttributeUpdates(updateItems);
			            
			@SuppressWarnings("unused")
			UpdateItemResult result = client.updateItem(updateItemRequest);
			//System.out.println(result.toString());
		}
		catch(JSONException jsone)
		{
			jsone.printStackTrace();
		}
		//System.out.println("MetricItem.setImpressions(): leaving");
	}
	
	@DynamoDBAttribute(attributeName="processing_time_in_ms")  
	public long getProcessingTimeInMS() {return processing_time_in_ms; }
	public void setProcessingTimeInMS(long processing_time_in_ms) { this.processing_time_in_ms = processing_time_in_ms; }
	
	@DynamoDBIgnore
	public int compareTo(MetricItem o) // this makes more recent comments come first
	{
		 Long othertimestamp = ((MetricItem)o).getMSFE();
		 int x = othertimestamp.compareTo(getMSFE());
		 if(x >= 0) // this is to prevent equals
			 return 1;
		 else
			 return -1;
	}
}