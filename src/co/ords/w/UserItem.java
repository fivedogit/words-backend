
package co.ords.w;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.UUID;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

@DynamoDBTable(tableName="words_users2")
public class UserItem implements java.lang.Comparable<UserItem> {

	// static parts of the database entry
	private String id;
	private String email;
	private boolean email_is_confirmed;
	private String provisional_email;
	private String provisional_email_confcode;
	private long provisional_email_confcode_msfe;
	private String screenname;
	private String avatar_icon;
	private String picture;
	private long since;
	private long seen;
	private String since_hr;
	private String seen_hr;
	private int overlay_size;
	private String onreply;
	private String onlike;
	private String ondislike;
	private String onmention;
	private String onfollowcomment;
	private String promos;// deprecated as of client v.203
	private long threadviews;
	private int notification_count;
	private Set<String> activity_ids;
	private double rating_in_window; // populated asynchronously via UserCalculator
	private double rating; // populated asynchronously via UserCalculator
	private long rating_ts; // populated asynchronously via UserCalculator
	private int up_in_window;
	private int down_in_window;
	private int up;
	private int down;
	private String this_access_token; 
	private long this_access_token_expires;
	private String permission_level; 
	private String last_login_type;
	private String last_useragent;
	private String last_browser;
	private long last_active_msfe;
	private String last_active_hr;
	private long last_like_msfe;
	private String last_like_hr;
	private long last_dislike_msfe;
	private String last_dislike_hr;
	private long last_comment_msfe;
	private String last_comment_hr;
	private long last_hpqsplike_msfe;
	private String last_hpqsplike_hr;
	private long last_hostnamelike_msfe;
	private String last_hostnamelike_hr;
	private int num_likes_authored; // populated asynchronously via UserCalculator
	private int num_dislikes_authored; // populated asynchronously via UserCalculator
	private int num_comments_authored; // populated asynchronously via UserCalculator
	private int num_likes_authored_in_window; // populated asynchronously via UserCalculator
	private int num_dislikes_authored_in_window; // populated asynchronously via UserCalculator
	private int num_comments_authored_in_window; // populated asynchronously via UserCalculator
	private String salt;
	private String encrypted_password;
	private String temp_plaintext_password;
	private boolean shared_to_facebook; // giveaway-specific, for now
	private boolean shared_to_twitter; // giveaway-specific, for now
	private boolean show_footer_messages;
	private String password_reset_confcode;
	private long password_reset_confcode_msfe;
	private String last_ip_address;
	
	// dynamic parts (not in the database entry itself). NOTE: these can differ based on window size (or no window at all)
	private TreeSet<CommentItem> comments_authored;
	private TreeSet<LikeItem> likes_authored;
	private TreeSet<DislikeItem> dislikes_authored;

	@DynamoDBHashKey(attributeName="id") 
	public String getId() {return id; }
	public void setId(String id) { this.id = id; }
	
	@DynamoDBIndexHashKey(attributeName="screenname", globalSecondaryIndexName="screenname-index") // screenname is the hash key for screenname-email
	public String getScreenname() {return screenname; }
	public void setScreenname(String screenname) { this.screenname = screenname; }
	
	@DynamoDBIndexHashKey(attributeName="email", globalSecondaryIndexName="email-index") // email is the hash key for email-screenname
	public String getEmail() { return email;}
	public void setEmail(String email) { this.email = email; }
	
	@DynamoDBAttribute(attributeName="email_is_confirmed")  
	public boolean getEmailIsConfirmed() {return email_is_confirmed; }
	public void setEmailIsConfirmed(boolean email_is_confirmed) { this.email_is_confirmed = email_is_confirmed; }
	
	@DynamoDBAttribute(attributeName="provisional_email")  
	public String getProvisionalEmail() {return provisional_email; }
	public void setProvisionalEmail(String provisional_email) { this.provisional_email = provisional_email; }
	
	@DynamoDBAttribute(attributeName="provisional_email_confcode")  
	public String getProvisionalEmailConfirmationCode() {return provisional_email_confcode; }
	public void setProvisionalEmailConfirmationCode(String provisional_email_confcode) { this.provisional_email_confcode = provisional_email_confcode; }
	
	@DynamoDBAttribute(attributeName="provisional_email_confcode_msfe")  
	public long getProvisionalEmailConfirmationCodeMSFE() {return provisional_email_confcode_msfe; }
	public void setProvisionalEmailConfirmationCodeMSFE(long provisional_email_confcode_msfe) { this.provisional_email_confcode_msfe = provisional_email_confcode_msfe; }
	
	@DynamoDBAttribute(attributeName="avatar_icon")  
	public String getAvatarIcon() {return avatar_icon; }
	public void setAvatarIcon(String avatar_icon) { this.avatar_icon = avatar_icon; }

	@DynamoDBAttribute(attributeName="permission_level")  
	public String getPermissionLevel() {return permission_level; }
	public void setPermissionLevel(String permission_level) { this.permission_level = permission_level; }
	
	@DynamoDBAttribute(attributeName="picture")  
	public String getPicture() {return picture; }
	public void setPicture(String picture) { this.picture = picture; }
	
	@DynamoDBAttribute(attributeName="since")  
	public long getSince() {return since; }
	public void setSince(long since) { this.since = since; }
	
	@DynamoDBAttribute(attributeName="seen")  
	public long getSeen() {return seen; }
	public void setSeen(long seen) { this.seen = seen; }
	
	@DynamoDBAttribute(attributeName="this_access_token")  
	public String getThisAccessToken() {return this_access_token; }
	public void setThisAccessToken(String this_access_token) { this.this_access_token = this_access_token; }
	
	@DynamoDBAttribute(attributeName="this_access_token_expires")  
	public long getThisAccessTokenExpires() {return this_access_token_expires; }
	public void setThisAccessTokenExpires(long this_access_token_expires) { this.this_access_token_expires = this_access_token_expires; }
	
	@DynamoDBAttribute(attributeName="overlay_size")  
	public int getOverlaySize() {return overlay_size; }
	public void setOverlaySize(int overlay_size) { this.overlay_size = overlay_size; }
	
	@DynamoDBAttribute(attributeName="onreply")  
	public String getOnReply() {return onreply; }
	public void setOnReply(String onreply) { this.onreply = onreply; }
	
	@DynamoDBAttribute(attributeName="onlike")  
	public String getOnLike() {return onlike; }
	public void setOnLike(String onlike) { this.onlike = onlike; }
	
	@DynamoDBAttribute(attributeName="ondislike")  
	public String getOnDislike() {return ondislike; }
	public void setOnDislike(String ondislike) { this.ondislike = ondislike; }
	
	@DynamoDBAttribute(attributeName="onmention")  
	public String getOnMention() {return onmention; }
	public void setOnMention(String onmention) { this.onmention = onmention; }
	
	@DynamoDBAttribute(attributeName="onfollowcomment")  
	public String getOnFollowComment() {return onfollowcomment; }
	public void setOnFollowComment(String onfollowcomment) { this.onfollowcomment = onfollowcomment; }
	
	@DynamoDBAttribute(attributeName="promos")  // deprecated as of client v.203
	public String getPromos() {return promos; }// deprecated as of client v.203
	public void setPromos(String promos) { this.promos = promos; }// deprecated as of client v.203
 
	@DynamoDBAttribute(attributeName="notification_count")  
	public int getNotificationCount() { return notification_count; }
	public void setNotificationCount(int notification_count) { this.notification_count = notification_count; }
	
	@DynamoDBAttribute(attributeName="activity_ids")  
	public Set<String> getActivityIds() { return activity_ids; }
	public void setActivityIds(Set<String> activity_ids) { this.activity_ids = activity_ids; }
	
	@DynamoDBAttribute(attributeName="threadviews")  
	public long getThreadViews() {return threadviews; }
	public void setThreadViews(long threadviews) { this.threadviews = threadviews; }
	
	@DynamoDBAttribute(attributeName="rating")  
	public double getRating() {return rating; }
	public void setRating(double rating) { this.rating = rating; }
	
	@DynamoDBAttribute(attributeName="rating_in_window")  
	public double getRatingInWindow() {return rating_in_window; }
	public void setRatingInWindow(double rating_in_window) { this.rating_in_window = rating_in_window; }
	
	@DynamoDBAttribute(attributeName="rating_ts")  
	public long getRatingTS() {return rating_ts; }
	public void setRatingTS(long rating_ts) { this.rating_ts = rating_ts; }
	
	@DynamoDBAttribute(attributeName="up")  
	public int getUp() {return up; }
	public void setUp(int up) { this.up = up; }
	
	@DynamoDBAttribute(attributeName="down")  
	public int getDown() {return down; }
	public void setDown(int down) { this.down = down; }
	
	@DynamoDBAttribute(attributeName="up_in_window")  
	public int getUpInWindow() {return up_in_window; }
	public void setUpInWindow(int up_in_window) { this.up_in_window = up_in_window; }
	
	@DynamoDBAttribute(attributeName="down_in_window")  
	public int getDownInWindow() {return down_in_window; }
	public void setDownInWindow(int down_in_window) { this.down_in_window = down_in_window; }

	@DynamoDBIgnore
	public boolean getCommentVirgin() {return (last_comment_msfe == 0); }

	@DynamoDBAttribute(attributeName="last_login_type")  
	public String getLastLoginType() {return last_login_type; }
	public void setLastLoginType(String last_login_type) { this.last_login_type = last_login_type; }
	
	@DynamoDBAttribute(attributeName="last_useragent")  
	public String getLastUserAgent() {return last_useragent; }
	public void setLastUserAgent(String last_useragent) { this.last_useragent = last_useragent; }
	
	@DynamoDBAttribute(attributeName="last_browser")  
	public String getLastBrowser() {return last_browser; }
	public void setLastBrowser(String last_browser) { this.last_browser = last_browser; }
	
	@DynamoDBAttribute(attributeName="last_active_msfe")  
	public long getLastActiveMSFE() {return last_active_msfe; }
	public void setLastActiveMSFE(long last_active_msfe) { this.last_active_msfe = last_active_msfe; }
	
	@DynamoDBAttribute(attributeName="last_active_hr")  
	public String getLastActiveHumanReadable() {return last_active_hr; }  // note this should not be used. Always format and return the msfe value instead.
	public void setLastActiveHumanReadable(String last_active_hr) { this.last_active_hr = last_active_hr; }
	
	@DynamoDBAttribute(attributeName="last_comment_msfe")  
	public long getLastCommentMSFE() {return last_comment_msfe; }
	public void setLastCommentMSFE(long last_comment_msfe) { this.last_comment_msfe = last_comment_msfe; }
	
	@DynamoDBAttribute(attributeName="last_comment_hr")  
	public String getLastCommentHumanReadable() {return last_comment_hr; }  // note this should not be used. Always format and return the msfe value instead.
	public void setLastCommentHumanReadable(String last_comment_hr) { this.last_comment_hr = last_comment_hr; }
	
	@DynamoDBAttribute(attributeName="last_like_msfe")  
	public long getLastLikeMSFE() {return last_like_msfe; }
	public void setLastLikeMSFE(long last_like_msfe) { this.last_like_msfe = last_like_msfe; }
	
	@DynamoDBAttribute(attributeName="last_like_hr")  
	public String getLastLikeHumanReadable() {return last_like_hr; }  // note this should not be used. Always format and return the msfe value instead.
	public void setLastLikeHumanReadable(String last_like_hr) { this.last_like_hr = last_like_hr; }

	@DynamoDBAttribute(attributeName="last_dislike_msfe")  
	public long getLastDislikeMSFE() {return last_dislike_msfe; }
	public void setLastDislikeMSFE(long last_dislike_msfe) { this.last_dislike_msfe = last_dislike_msfe; }
	
	@DynamoDBAttribute(attributeName="last_dislike_hr")  
	public String getLastDislikeHumanReadable() {return last_dislike_hr; }  // note this should not be used. Always format and return the msfe value instead.
	public void setLastDislikeHumanReadable(String last_dislike_hr) { this.last_dislike_hr = last_dislike_hr; }
	
	@DynamoDBAttribute(attributeName="last_hpqsplike_msfe")  
	public long getLastHPQSPLikeMSFE() {return last_hpqsplike_msfe; }
	public void setLastHPQSPLikeMSFE(long last_hpqsplike_msfe) { this.last_hpqsplike_msfe = last_hpqsplike_msfe; }
	
	@DynamoDBAttribute(attributeName="last_hpqsplike_hr")  
	public String getLastHPQSPLikeHumanReadable() {return last_hpqsplike_hr; }  // note this should not be used. Always format and return the msfe value instead.
	public void setLastHPQSPLikeHumanReadable(String last_hpqsplike_hr) { this.last_hpqsplike_hr = last_hpqsplike_hr; }
	
	@DynamoDBAttribute(attributeName="last_hostnamelike_msfe")  
	public long getLastHostnameLikeMSFE() {return last_hostnamelike_msfe; }
	public void setLastHostnameLikeMSFE(long last_hostnamelike_msfe) { this.last_hostnamelike_msfe = last_hostnamelike_msfe; }
	
	@DynamoDBAttribute(attributeName="last_hostnamelike_hr")  
	public String getLastHostnameLikeHumanReadable() {return last_hostnamelike_hr; }  // note this should not be used. Always format and return the msfe value instead.
	public void setLastHostnameLikeHumanReadable(String last_hostnamelike_hr) { this.last_hostnamelike_hr = last_hostnamelike_hr; }
	
	@DynamoDBAttribute(attributeName="since_hr")  
	public String getSinceHumanReadable() {return since_hr; } // note this should not be used. Always format and return the msfe value instead.
	public void setSinceHumanReadable(String since_hr) { this.since_hr = since_hr; }
	
	@DynamoDBAttribute(attributeName="seen_hr")  
	public String getSeenHumanReadable() {return seen_hr; } // note this should not be used. Always format and return the msfe value instead.
	public void setSeenHumanReadable(String seen_hr) { this.seen_hr = seen_hr; }
	
	@DynamoDBAttribute(attributeName="num_likes_authored_in_window")  
	public int getNumLikesAuthoredInWindow() { return num_likes_authored_in_window; }
	public void setNumLikesAuthoredInWindow(int num_likes_authored_in_window) { this.num_likes_authored_in_window = num_likes_authored_in_window; }
	
	@DynamoDBAttribute(attributeName="num_dislikes_authored_in_window")  
	public int getNumDislikesAuthoredInWindow() { return num_dislikes_authored_in_window; }
	public void setNumDislikesAuthoredInWindow(int num_dislikes_authored_in_window) { this.num_dislikes_authored_in_window = num_dislikes_authored_in_window; }
	
	@DynamoDBAttribute(attributeName="num_comments_authored_in_window")  
	public int getNumCommentsAuthoredInWindow() { return num_comments_authored_in_window; }
	public void setNumCommentsAuthoredInWindow(int num_comments_authored_in_window) { this.num_comments_authored_in_window = num_comments_authored_in_window; }
	
	@DynamoDBAttribute(attributeName="num_likes_authored")  
	public int getNumLikesAuthored() { return num_likes_authored; }
	public void setNumLikesAuthored(int num_likes_authored) { this.num_likes_authored = num_likes_authored; }
	
	@DynamoDBAttribute(attributeName="num_dislikes_authored")  
	public int getNumDislikesAuthored() { return num_dislikes_authored; }
	public void setNumDislikesAuthored(int num_dislikes_authored) { this.num_dislikes_authored = num_dislikes_authored; }
	
	@DynamoDBAttribute(attributeName="num_comments_authored")  
	public int getNumCommentsAuthored() { return num_comments_authored; }
	public void setNumCommentsAuthored(int num_comments_authored) { this.num_comments_authored = num_comments_authored; }
	
	@DynamoDBAttribute(attributeName="salt")  
	public String getSalt() {return salt; }  
	public void setSalt(String salt) { this.salt = salt; }
	
	@DynamoDBAttribute(attributeName="encrypted_password")  
	public String getEncryptedPassword() {return encrypted_password; }  
	public void setEncryptedPassword(String encrypted_password) { this.encrypted_password = encrypted_password; }
	
	// to be used only once, emailed to users after backend upgrade, then deleted
	@DynamoDBAttribute(attributeName="temp_plaintext_password")  
	public String getTempPlaintextPassword() {return temp_plaintext_password; }  
	public void setTempPlaintextPassword(String temp_plaintext_password) { this.temp_plaintext_password = temp_plaintext_password; }
	
	@DynamoDBAttribute(attributeName="shared_to_facebook")  
	public boolean getSharedToFacebook() {return shared_to_facebook; }
	public void setSharedToFacebook(boolean shared_to_facebook) { this.shared_to_facebook = shared_to_facebook; }
	
	@DynamoDBAttribute(attributeName="shared_to_twitter")  
	public boolean getSharedToTwitter() {return shared_to_twitter; }
	public void setSharedToTwitter(boolean shared_to_twitter) { this.shared_to_twitter = shared_to_twitter; }
	
	@DynamoDBAttribute(attributeName="show_footer_messages")  
	public boolean getShowFooterMessages() {return show_footer_messages; }
	public void setShowFooterMessages(boolean show_footer_messages) { this.show_footer_messages = show_footer_messages; }
	
	@DynamoDBAttribute(attributeName="password_reset_confcode")  
	public String getPasswordResetConfirmationCode() {return password_reset_confcode; }  
	public void setPasswordResetConfirmationCode(String password_reset_confcode) { this.password_reset_confcode = password_reset_confcode; }
	
	@DynamoDBAttribute(attributeName="password_reset_confcode_msfe")  
	public long getPasswordResetConfirmationCodeMSFE() {return password_reset_confcode_msfe; }
	public void setPasswordResetConfirmationCodeMSFE(long password_reset_confcode_msfe) { this.password_reset_confcode_msfe = password_reset_confcode_msfe; }
	
	@DynamoDBAttribute(attributeName="last_ip_address")  
	public String getLastIPAddress() {return last_ip_address; }  
	public void setLastIPAddress(String last_ip_address) { this.last_ip_address = last_ip_address; }
	
	@DynamoDBIgnore
	public boolean isCurrentPassword(String password_to_test)
	{
		String mortons = this.getSalt();
		String current_encrypted_password = this.getEncryptedPassword();
		if(mortons == null || current_encrypted_password == null) // one of these required values is not yet set
			return false;
		SHARight sr = new SHARight();
		String proposed_encrypted_password = sr.get_SHA_512_SecurePassword(password_to_test, mortons);
		if(proposed_encrypted_password.equals(current_encrypted_password))
			return true;
		else
			return false;
	}
	
	
	@DynamoDBIgnore // if minutes_ago == 0 , get all
	public TreeSet<CommentItem> getCommentsAuthored(int minutes_ago, WordsMapper mapper, DynamoDBMapperConfig dynamo_config) 
	{ 
		DynamoDBQueryExpression<CommentItem> queryExpression = new DynamoDBQueryExpression<CommentItem>()
				.withIndexName("author_id-msfe-index")
				.withScanIndexForward(true)
				.withConsistentRead(false);
	        
	     // set the screenname part
        CommentItem commentKey = new CommentItem();
        commentKey.setAuthorId(getId());
        queryExpression.setHashKeyValues(commentKey);
        
        if(minutes_ago > 0)
        {
        	//System.out.println("Getting comments authored with a valid cutoff time.");
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
        List<CommentItem> commentitems = mapper.query(CommentItem.class, queryExpression, dynamo_config);
        if(commentitems != null && commentitems.size() > 0)
        	comments_authored = new TreeSet<CommentItem>();
        for (CommentItem commentitem : commentitems) {
            //System.out.format("Parent=%s, Id=%s",
            //       dislikeitem.getParent(), dislikeitem.getId());
        	comments_authored.add(commentitem);
        }
		return comments_authored;
	}
	
	@DynamoDBIgnore
	public TreeSet<LikeItem> getLikesAuthored(int minutes_ago, WordsMapper mapper, DynamoDBMapperConfig dynamo_config) { 
		DynamoDBQueryExpression<LikeItem> queryExpression = new DynamoDBQueryExpression<LikeItem>()
				.withIndexName("author_id-msfe-index")
				.withScanIndexForward(true)
				.withConsistentRead(false);
		
		   // set the screenname part
        LikeItem likeKey = new LikeItem();
        likeKey.setAuthorId(getId());
        queryExpression.setHashKeyValues(likeKey);
        
        if(minutes_ago > 0)
        {
        	//System.out.println("Getting likes authored with a valid cutoff time.");
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
        List<LikeItem> likeitems = mapper.query(LikeItem.class, queryExpression, dynamo_config);
        if(likeitems != null && likeitems.size() > 0)
        	likes_authored = new TreeSet<LikeItem>();
        for (LikeItem likeitem : likeitems) {
            //System.out.format("Parent=%s, Id=%s",
            //       dislikeitem.getParent(), dislikeitem.getId());
        	likes_authored.add(likeitem);
        }	
		return likes_authored;
	}
	
	@DynamoDBIgnore
	public TreeSet<DislikeItem> getDislikesAuthored(int minutes_ago, WordsMapper mapper, DynamoDBMapperConfig dynamo_config) { 
		// set up an expression to query screename#id
        DynamoDBQueryExpression<DislikeItem> queryExpression = new DynamoDBQueryExpression<DislikeItem>()
				.withIndexName("author_id-msfe-index")
				.withScanIndexForward(true)
				.withConsistentRead(false);
        
        // set the screenname part
        DislikeItem dislikeKey = new DislikeItem();
        dislikeKey.setAuthorId(getId());
        queryExpression.setHashKeyValues(dislikeKey);
        
        // set the msfe range part
        if(minutes_ago > 0)
        {
        	//System.out.println("Getting dislikes authored with a valid cutoff time.");
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
        List<DislikeItem> dislikeitems = mapper.query(DislikeItem.class, queryExpression, dynamo_config);
        if(dislikeitems != null && dislikeitems.size() > 0)
        	dislikes_authored = new TreeSet<DislikeItem>();
        for (DislikeItem dislikeitem : dislikeitems) {
            //System.out.format("Parent=%s, Id=%s",
            //       dislikeitem.getParent(), dislikeitem.getId());
        	dislikes_authored.add(dislikeitem);
        }
		return dislikes_authored;
	}
	
	@DynamoDBIgnore
	public boolean isValid(String inc_this_access_token)
	{
		if(inc_this_access_token == null)
			return false;
		long now = System.currentTimeMillis();
		if(getThisAccessToken().equals(inc_this_access_token) && getThisAccessTokenExpires() >= now)
			return true;
		else
			return false;
	}
	
	@DynamoDBIgnore
	public void hideAllComments(WordsMapper mapper, DynamoDBMapperConfig dynamo_config)
	{
		TreeSet<CommentItem> commentitems = getCommentsAuthored(0, mapper, dynamo_config); // 0 == get all
		CommentItem current = null;
		Iterator<CommentItem> ci_it = commentitems.iterator();
		while(ci_it.hasNext())
		{
			current = ci_it.next();
			current.setHidden(true);
			mapper.save(current);
		}
		return;
	}
	
	@DynamoDBIgnore
	public double getOrCalcRatingInWindow(WordsMapper mapper, DynamoDBMapperConfig dynamo_config, boolean force)
	{
		if(force == false)
		{
			Calendar cal = Calendar.getInstance();
			long now = cal.getTimeInMillis();
			long rating_ttl = 100; // 100 minutes (default in case database value is missing)
			GlobalvarItem gvi = mapper.load(GlobalvarItem.class, "user_rating_calc_ttl_mins", dynamo_config);
			if(gvi != null)
			{
				rating_ttl = gvi.getNumberValue();
				if(rating_ttl == 0 || rating_ttl > 1440) // sanity check
					rating_ttl = 100; // if rating_ttl comes back from db as zero or more than one day, then the rating value has gotten messed up somehow. Set back to default value of 100;
			}
			rating_ttl = rating_ttl * 60000; // convert minutes to milliseconds 
			if((now - getRatingTS()) < rating_ttl)
				return getRatingInWindow();
			else
			{
				//System.out.println("Elapsed since last rating calc = " + now + " - " + getRatingTS() + "=" + (now - getRatingTS()) + " which was > rating_ttl=" + rating_ttl);
				UserCalculator uc = new UserCalculator(this, mapper, dynamo_config);
				uc.start();
				return getRatingInWindow();
			}
		}
		else
		{
			//System.out.println("2Before calculation " + getRating());
			UserCalculator uc = new UserCalculator(this, mapper, dynamo_config);
			uc.start();
			return getRatingInWindow();
		}
		
	}

	@DynamoDBIgnore
	public JSONObject getAsJSONObject(boolean get_email, boolean get_this_access_token, boolean get_activity_ids, boolean get_notification_preferences,
			boolean get_notification_count, boolean get_seen, AmazonDynamoDBClient client, WordsMapper mapper, DynamoDBMapperConfig dynamo_config)
	{
		JSONObject user_jo = new JSONObject();
		try {
			user_jo.put("screenname", getScreenname());
			user_jo.put("picture", getPicture());
			user_jo.put("last_login_type", getLastLoginType());
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z"); // return msfe values formatted like this. 
			sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
			// so we can change the formatting to whatever we want here, regardless of what is in the (meaningless) "HumanReadable" columns in the database
			
			user_jo.put("num_likes_authored", getNumLikesAuthored());
			user_jo.put("num_dislikes_authored", getNumDislikesAuthored());
			user_jo.put("num_comments_authored", getNumCommentsAuthored());
			user_jo.put("num_likes_authored_in_window", getNumLikesAuthoredInWindow());
			user_jo.put("num_dislikes_authored_in_window", getNumDislikesAuthoredInWindow());
			user_jo.put("num_comments_authored_in_window", getNumCommentsAuthoredInWindow());
			
			user_jo.put("since", sdf.format(getSince()));
			user_jo.put("overlay_size", getOverlaySize());
			
			if(get_email) 
			{
				int giveaway_entries = 0;
				if(this.getEmailIsConfirmed() && this.getNumCommentsAuthored() > 0)
				{
					giveaway_entries = 1;
					if(this.getSharedToFacebook())
						giveaway_entries++;
					if(this.getSharedToTwitter())
						giveaway_entries++;
				}
				user_jo.put("giveaway_entries", giveaway_entries);
				user_jo.put("shared_to_facebook", this.getSharedToFacebook());
				user_jo.put("shared_to_twitter", this.getSharedToTwitter());
				user_jo.put("show_footer_messages", this.getShowFooterMessages());
				user_jo.put("email", getEmail());
				user_jo.put("email_is_confirmed", this.getEmailIsConfirmed());
				long ms_provisional_email_is_valid = 600000; // ten minutes
				if(this.getProvisionalEmailConfirmationCodeMSFE() != 0L) // a provisional code msfe exists
				{
					if((System.currentTimeMillis() - getProvisionalEmailConfirmationCodeMSFE()) < ms_provisional_email_is_valid) // and it hasn't expired yet
						user_jo.put("provisional_email", getProvisionalEmail());
					else // it has expired // clear out the code, the msfe and the provisional email
					{
						this.setProvisionalEmailConfirmationCodeMSFE(0);
						this.setProvisionalEmailConfirmationCode(null);
						this.setProvisionalEmail(null);
						mapper.save(this);
					}
				}
			}
			if(get_this_access_token)
			{
				user_jo.put("this_access_token", getThisAccessToken());
				user_jo.put("permission_level", getPermissionLevel());
			}
			
			user_jo.put("rating_in_window", getOrCalcRatingInWindow(mapper, dynamo_config, false)); 
			user_jo.put("up_in_window", getUpInWindow());
			user_jo.put("down_in_window", getDownInWindow());
			
			user_jo.put("rating", getRating()); 
			user_jo.put("up", getUp());
			user_jo.put("down", getDown());
			
			long rating_window_mins = 10080; //set to sane value, then attempt to retrieve actual value //10080 = one week in minutes
			GlobalvarItem gvi = mapper.load(GlobalvarItem.class, "rating_window_mins", dynamo_config);
			if(gvi != null)
				rating_window_mins = gvi.getNumberValue();
			user_jo.put("rating_window_mins", rating_window_mins); 
			
			if(get_seen)
				user_jo.put("seen", sdf.format(getSeen()));
			
			if(get_notification_count)
				user_jo.put("notification_count", getNotificationCount());
			
			if(get_activity_ids)
			{
				// if more than 25 activity ids, trim the list down to 25
				if(getActivityIds() != null && getActivityIds().size() > 25)
				{
					TreeSet<String> tempset = new TreeSet<String>();
					tempset.addAll(getActivityIds());
					Iterator<String> tempit = tempset.descendingIterator();
					String currentstring = null;
					Set<String> newset = new TreeSet<String>();
					int x = 0;
					while(tempit.hasNext() && x < 25)
					{
						currentstring = tempit.next();
						//System.out.println("Descending " + currentstring + " and " + Global.fromOtherBaseToDecimal(62, currentstring));
						newset.add(currentstring);
						x++;
					}
					this.setActivityIds(newset);
					mapper.save(this);
					user_jo.put("activity_ids", newset);
				}
				else
					user_jo.put("activity_ids", getActivityIds());
				
			}
			if(get_notification_preferences)
			{
				user_jo.put("onreply", getOnReply());
				user_jo.put("onlike", getOnLike());
				user_jo.put("ondislike", getOnDislike());
				user_jo.put("onmention", getOnMention());
				user_jo.put("onfollowcomment", getOnFollowComment());
				user_jo.put("promos", getPromos()); // deprecated as of client v.203
				if(this.getShowFooterMessages())
					user_jo.put("footermessages", "show");
				else
					user_jo.put("footermessages", "hide");
			}
			
			// internal accounts and admins can get all other NON-ADMIN internal accounts
			// getting back to an admin account requires the user to log out and back in.
			if((getEmail().endsWith("@words4chrome.com") || getEmail().endsWith("@ords.co") || getPermissionLevel().equals("admin")) && (getSince() < 1407163424000L))
			{	
				//System.out.println("UserItem.getAsJSON(): Getting alts");
				JSONArray alts_ja = new JSONArray();
				JSONObject alt_jo = null;
				DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
				List<UserItem> scanResult = mapper.scan(UserItem.class, scanExpression);
				
				if(scanResult != null && scanResult.size() > 0)
				{	
					//System.out.println("UserItem.getAsJSON(): Getting alts. Scanresult > 0");
					for (UserItem useritem : scanResult) {
						//System.out.println("UserItem.getAsJSON(): email=" + useritem.getEmail()); //1407337263479
						if((useritem.getEmail().endsWith("@words4chrome.com") || useritem.getEmail().endsWith("@ords.co")) && !useritem.getPermissionLevel().equals("admin") && (useritem.getSince() < 1407163424000L))
						{	
							alt_jo = new JSONObject();
							alt_jo.put("screenname", useritem.getScreenname());
							alt_jo.put("email", useritem.getEmail());
							alt_jo.put("this_access_token", useritem.getThisAccessToken());
							alts_ja.put(alt_jo);
						}
					}
					if(alts_ja.length() > 0)
						user_jo.put("alts", alts_ja);
				}
			}
			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return user_jo;
	}
	
	
	@DynamoDBIgnore
	public boolean hasLikedHPQSP(String hpqsp_string, WordsMapper mapper, DynamoDBMapperConfig dynamo_config)
	{
		// set up an expression to query screename#id
		DynamoDBQueryExpression<HPQSPLikeItem> queryExpression = new DynamoDBQueryExpression<HPQSPLikeItem>()
				.withIndexName("author_id-hpqsp-index")
				.withScanIndexForward(true)
				.withConsistentRead(false);
	        
		// set the screenname part
		HPQSPLikeItem key = new HPQSPLikeItem();
		key.setAuthorId(getId());
		queryExpression.setHashKeyValues(key);
	        
		// set the hpqsp range part
		Map<String, Condition> keyConditions = new HashMap<String, Condition>();
		keyConditions.put("hpqsp",new Condition()
			.withComparisonOperator(ComparisonOperator.EQ)
			.withAttributeValueList(new AttributeValue().withS(hpqsp_string)));
		queryExpression.setRangeKeyConditions(keyConditions);	

		//System.out.println("WORDSCore.userHasLikedHPQSP(): executing with author_id="+ author_id + " and hpqsp=" + hpqsp_string);
		// execute
		List<HPQSPLikeItem> items = mapper.query(HPQSPLikeItem.class, queryExpression, dynamo_config);
		if(items != null && items.size() > 0)
			return true;
		else 
			return false;
	}
		
	@DynamoDBIgnore
	public boolean hasLikedHostname(String hostname_string, WordsMapper mapper, DynamoDBMapperConfig dynamo_config)
	{
		// set up an expression to query screename#id
		DynamoDBQueryExpression<HostnameLikeItem> queryExpression = new DynamoDBQueryExpression<HostnameLikeItem>()
				.withIndexName("author_id-hostname-index")
				.withScanIndexForward(true)
				.withConsistentRead(false);
	        
		// set the screenname part
		HostnameLikeItem key = new HostnameLikeItem();
		key.setAuthorId(getId());
		queryExpression.setHashKeyValues(key);
	        
		// set the hostname range part
		Map<String, Condition> keyConditions = new HashMap<String, Condition>();
		keyConditions.put("hostname",new Condition()
			.withComparisonOperator(ComparisonOperator.EQ)
			.withAttributeValueList(new AttributeValue().withS(hostname_string)));
		queryExpression.setRangeKeyConditions(keyConditions);

		//System.out.println("WORDSCore.userHasLikedHostname(): executing with author_id="+ author_id + " and hostname=" + hostname_string);
		// execute
		List<HostnameLikeItem> items = mapper.query(HostnameLikeItem.class, queryExpression, dynamo_config);
		if(items != null && items.size() > 0)
			return true;
		else 
			return false;
	}
	    
	@DynamoDBIgnore
	public boolean hasLikedComment(String comment_id, WordsMapper mapper, DynamoDBMapperConfig dynamo_config)
	{
		// set up an expression to query screename#id
		DynamoDBQueryExpression<LikeItem> queryExpression = new DynamoDBQueryExpression<LikeItem>()
				.withIndexName("author_id-parent-index")
				.withScanIndexForward(true)
				.withConsistentRead(false);
	        
		// set the screenname part
		LikeItem key = new LikeItem();
		key.setAuthorId(getId());
		queryExpression.setHashKeyValues(key);
	        
		// set the hostname range part
		Map<String, Condition> keyConditions = new HashMap<String, Condition>();
		keyConditions.put("parent",new Condition()
			.withComparisonOperator(ComparisonOperator.EQ)
			.withAttributeValueList(new AttributeValue().withS(comment_id)));
		queryExpression.setRangeKeyConditions(keyConditions);

		//System.out.println("WORDSCore.userHasLikedComment(): executing with author_id="+ author_id + " and comment_id=" + comment_id);
		// execute
		List<LikeItem> items = mapper.query(LikeItem.class, queryExpression, dynamo_config);
		if(items != null && items.size() > 0)
			return true;
		else 
			return false;
	}
	    
	@DynamoDBIgnore
	public boolean hasDislikedComment(String comment_id, WordsMapper mapper, DynamoDBMapperConfig dynamo_config)
	{
		// set up an expression to query screename#id
		DynamoDBQueryExpression<DislikeItem> queryExpression = new DynamoDBQueryExpression<DislikeItem>()
				.withIndexName("author_id-parent-index")
				.withScanIndexForward(true)
				.withConsistentRead(false);
	        
		// set the screenname part
		DislikeItem key = new DislikeItem();
		key.setAuthorId(getId());
		queryExpression.setHashKeyValues(key);
	        
		// set the hostname range part
		Map<String, Condition> keyConditions = new HashMap<String, Condition>();
		keyConditions.put("parent",new Condition()
			.withComparisonOperator(ComparisonOperator.EQ)
			.withAttributeValueList(new AttributeValue().withS(comment_id)));
		queryExpression.setRangeKeyConditions(keyConditions);
		
		//System.out.println("WORDSCore.userHasDislikedComment(): executing with author_id="+ author_id + " and comment_id=" + comment_id);
		// execute
		List<DislikeItem> items = mapper.query(DislikeItem.class, queryExpression, dynamo_config);
		if(items != null && items.size() > 0)
			return true;
		else 
			return false;
	}
	
	@DynamoDBIgnore
	public boolean sendEmailConfirmationCode(String provisional_email, String code)
	{
		String textbody = "An email address (" + provisional_email + ") was added to your account. BUT YOU'RE NOT DONE! Enter this code on the profile/settings tab to confirm it.";
		textbody = textbody + "\n\n\t" + code + "";
		textbody = textbody + "\n\nIf you did not initiate this action, you may ignore this message.";
		String htmlbody = "";
		htmlbody = htmlbody + "<div style=\"width:100%;background-color:#333333;text-align:left;padding:5px\">";
		htmlbody = htmlbody + "<span style=\"padding:5px;color:white;font-family:'Times New Roman', Times, serif;font-size:20px;font-weight:bold\">W O R D S</span>";
		htmlbody = htmlbody + "</div>";
		htmlbody = htmlbody + "<div style=\"font-size:12px;font-family:arial,helvetica;color:black;text-align:left;padding-top:10px\">";
		htmlbody = htmlbody + "An email address (" + provisional_email + ") was added to your account. BUT YOU'RE NOT DONE! Enter this code on the profile/settings tab to confirm it.";
		htmlbody = htmlbody + "<p style=\"padding-left:20px\">" + code + "</p>";
		htmlbody = htmlbody + "<p>If you did not initiate this action, you may ignore this message.</p>";
		htmlbody = htmlbody + "</div>";

		SimpleEmailer se = new SimpleEmailer("New email address added", textbody, htmlbody, provisional_email, "info@words4chrome.com");
		se.start();	
		return true;
	}
	
	@DynamoDBIgnore
	public boolean sendPasswordResetEmail(WordsMapper mapper, DynamoDBMapperConfig dynamo_config)
	{
		String confcode = UUID.randomUUID().toString().replaceAll("-","");
		this.setPasswordResetConfirmationCode(confcode);
		this.setPasswordResetConfirmationCodeMSFE(System.currentTimeMillis());
		mapper.save(this);
		String textbody = "A request was made to reset your WORDS password. Copy and paste this code into the form to complete the password reset.";
		textbody = textbody + "\n\n\t" + confcode + "";
		textbody = textbody + "\n\nIf you did not initiate this action, ignore this message.";
		String htmlbody = "";
		htmlbody = htmlbody + "<div style=\"width:100%;background-color:#333333;text-align:left;padding:5px\">";
		htmlbody = htmlbody + "<span style=\"padding:5px;color:white;font-family:'Times New Roman', Times, serif;font-size:20px;font-weight:bold\">W O R D S</span>";
		htmlbody = htmlbody + "</div>";
		htmlbody = htmlbody + "<div style=\"font-size:12px;font-family:arial,helvetica;color:black;text-align:left;padding-top:10px\">";
		htmlbody = htmlbody + "A request was made to reset your WORDS password. Copy and paste this code into the form to complete the password reset.";
		htmlbody = htmlbody + "<p style=\"padding-left:20px\">" + confcode + "</p>";
		htmlbody = htmlbody + "<p>If you did not initiate this action, you may ignore this message.</p>";
		htmlbody = htmlbody + "</div>";

		SimpleEmailer se = new SimpleEmailer("Password reset request received", textbody, htmlbody, this.getEmail(), "info@words4chrome.com");
		se.start();	
		return true;
	}
	
	@DynamoDBIgnore
	public boolean emailNewlyResetPassword(String newpassword)
	{
		String textbody = "Your password has been successfully reset:";
		textbody = textbody + "\n\n\t" + newpassword;
		textbody = textbody + "\n\nPlease change this password as soon as possible via the Profile/Settings tab within WORDS before you forget it again. :)";
		String htmlbody = "";
		 htmlbody = htmlbody + "<div style=\"width:100%;background-color:#333333;text-align:left;padding:5px\">";
		 htmlbody = htmlbody + "<span style=\"padding:5px;color:white;font-family:'Times New Roman', Times, serif;font-size:20px;font-weight:bold\">W O R D S</span>";
		 htmlbody = htmlbody + "</div>";
		 htmlbody = htmlbody + "<div style=\"font-size:12px;font-family:arial,helvetica;color:black;text-align:left;padding-top:10px\">";
		 htmlbody = htmlbody + "Your password has been successfully reset:";
		 htmlbody = htmlbody + "<p style=\"padding-left:20px\">" + newpassword + "</p>";
		 htmlbody = htmlbody + "<p>Please change this password as soon as possible via the Profile/Settings tab within WORDS before you forget it again. :)</p>";
		 htmlbody = htmlbody + "</div>";
		 SimpleEmailer se = new SimpleEmailer("Your new password", textbody, htmlbody, this.getEmail(), "info@words4chrome.com");
		 se.start();	
		return true;
	}
	
	@DynamoDBIgnore
	public int compareTo(UserItem o) // this makes more recent comments come first
	{
	    String otherscreenname = ((UserItem)o).getScreenname();
	    int x = otherscreenname.compareTo(getScreenname());
	    if(x >= 0) // this is to prevent equals
	    	return 1;
	    else
	    	return -1;
	}
	
	public static void main(String [] args)
	{
	}
}
