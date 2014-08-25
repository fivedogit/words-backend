package co.ords.w;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

public class StatsSnapshotter extends java.lang.Thread {

	private WordsMapper mapper;
	private DynamoDBMapperConfig dynamo_config;
	private AmazonDynamoDBClient client;
	
	public void initialize()
	{
	}
	
	public StatsSnapshotter(WordsMapper inc_mapper, DynamoDBMapperConfig inc_dynamo_config, AmazonDynamoDBClient inc_client)
	{
		this.initialize();
		mapper = inc_mapper;
		dynamo_config = inc_dynamo_config;
		client = inc_client;
	}
	
	public void run()
	{
		System.out.println("=== " + super.getId() +  " Fired a StatsSnapshotter thread.");
		// temporary email to confirm snapshotter firings
		SimpleEmailer se = new SimpleEmailer("StatsSnapshotter fired!", "Snapshotter fired (text)", "Snapshotter fired (html)", "info@words4chrome.com", "info@words4chrome.com");
		se.start();
		
		 String uuid_str = UUID.randomUUID().toString().replaceAll("-","");
		
		Calendar tempcal = Calendar.getInstance();
		long before = tempcal.getTimeInMillis();
		tempcal.add(Calendar.DAY_OF_MONTH, -1);
		long msfe1 = tempcal.getTimeInMillis();
		tempcal.add(Calendar.DAY_OF_MONTH, -6);
		long msfe7 = tempcal.getTimeInMillis();
		tempcal.add(Calendar.DAY_OF_MONTH, -23);
		long msfe30 = tempcal.getTimeInMillis();
		
		DynamoDBScanExpression userScanExpression = new DynamoDBScanExpression();
		List<UserItem> userScanResult = mapper.scan(UserItem.class, userScanExpression, dynamo_config);
	
		// this could potentially get enormous. May have to shut off later.
		DynamoDBScanExpression threadviewScanExpression = new DynamoDBScanExpression();
		List<ThreadViewItem> threadviewScanResult = mapper.scan(ThreadViewItem.class, threadviewScanExpression, dynamo_config);
		
		//FIXME this should be a query
		DynamoDBScanExpression impressionScanExpression = new DynamoDBScanExpression();
		List<ImpressionItem> impressionScanResult = mapper.scan(ImpressionItem.class, impressionScanExpression, dynamo_config);
		
		MetricItem mi = new MetricItem();
		mi.setId(uuid_str);
		mi.setMSFE(before);
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
		sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
		
		mi.setTimestampHumanReadable(sdf.format(tempcal.getTimeInMillis()));

		GlobalvarItem gvi = mapper.load(GlobalvarItem.class, "thread_retrievals", dynamo_config);
		long thread_retrievals = 0L;
		if(gvi != null)
			thread_retrievals = gvi.getNumberValue();
		else
		{
			System.err.println("StatsSnapshotter couldn't find GVI thread_retrievals. Aborting");
			return;
		}
		
		// set metrics which have no time window
		mi.setRegisteredUsers(userScanResult.size());
		mi.setThreadRetrievals(thread_retrievals);
		mi.setThreadViews(threadviewScanResult.size()); // this could potentially get enormous. May have to shut off later.
				
		long google = 0L;
		long facebook = 0L;
		long native_login = 0L;
		long active30 = 0L;
		long active7 = 0L;
		long active1 = 0L;
		long commented30 = 0L;
		long commented7 = 0L;
		long commented1 = 0L;
		long authoredlike30 = 0L;
		long authoredlike7 = 0L;
		long authoredlike1 = 0L;
		long authoreddislike30 = 0L;
		long authoreddislike7 = 0L;
		long authoreddislike1 = 0L;
		long authoredhpqsplike30 = 0L;
		long authoredhpqsplike7 = 0L;
		long authoredhpqsplike1 = 0L;
		long authoredhostnamelike30 = 0L;
		long authoredhostnamelike7 = 0L;
		long authoredhostnamelike1 = 0L;
		long registered_commented_confirmed = 0L;
		for (UserItem useritem : userScanResult) { // this could get gigantic
			
			if(useritem.getNumCommentsAuthored() > 0 && useritem.getEmailIsConfirmed())
			{
				registered_commented_confirmed++;
			}
			if(useritem.getLastLoginType().equals("google"))
				google++;
			else if(useritem.getLastLoginType().equals("facebook"))
				facebook++;
			else
				native_login++;
			if(useritem.getLastActiveMSFE() > msfe30)
				active30++;
			if(useritem.getLastActiveMSFE() > msfe7)
				active7++;
			if(useritem.getLastActiveMSFE() > msfe1)
				active1++;
			if(useritem.getLastCommentMSFE() > msfe30)
				commented30++;
			if(useritem.getLastCommentMSFE() > msfe7)
				commented7++;
			if(useritem.getLastCommentMSFE() > msfe1)
				commented1++;
			if(useritem.getLastLikeMSFE() > msfe30)
				authoredlike30++;
			if(useritem.getLastLikeMSFE() > msfe7)
				authoredlike7++;
			if(useritem.getLastLikeMSFE() > msfe1)
				authoredlike1++;
			if(useritem.getLastDislikeMSFE() > msfe30)
				authoreddislike30++;
			if(useritem.getLastDislikeMSFE() > msfe7)
				authoreddislike7++;
			if(useritem.getLastDislikeMSFE() > msfe1)
				authoreddislike1++;
			if(useritem.getLastHPQSPLikeMSFE() > msfe30)
				authoredhpqsplike30++;
			if(useritem.getLastHPQSPLikeMSFE() > msfe7)
				authoredhpqsplike7++;
			if(useritem.getLastHPQSPLikeMSFE() > msfe1)
				authoredhpqsplike1++;
			if(useritem.getLastHostnameLikeMSFE() > msfe30)
				authoredhpqsplike30++;
			if(useritem.getLastHostnameLikeMSFE() > msfe7)
				authoredhpqsplike7++;
			if(useritem.getLastHostnameLikeMSFE() > msfe1)
				authoredhpqsplike1++;
		}
		//System.out.println("found " + registered_commented_confirmed + " registered_commented_confirmed");
		mi.setRegisteredCommentedConfirmed(registered_commented_confirmed);
		
		GlobalvarItem num_eligible_users_gvi = mapper.load(GlobalvarItem.class, "num_eligible_users", dynamo_config);
		num_eligible_users_gvi.setNumberValue(registered_commented_confirmed);
		mapper.save(num_eligible_users_gvi);
		
		mi.setGoogleLast(google);
		mi.setFacebookLast(facebook);
		mi.setNativeLast(native_login);
		
		// set user metrics
		mi.setActive30(active30);
		mi.setActive7(active7);
		mi.setActive1(active1);
		mi.setCommented30(commented30);
		mi.setCommented7(commented7);
		mi.setCommented1(commented1);
		mi.setAuthoredLike30(authoredlike30);
		mi.setAuthoredLike7(authoredlike7);
		mi.setAuthoredLike1(authoredlike1);
		mi.setAuthoredDislike30(authoreddislike30);
		mi.setAuthoredDislike7(authoreddislike7);
		mi.setAuthoredDislike1(authoreddislike1);
		mi.setAuthoredHPQSPLike30(authoredhpqsplike30);
		mi.setAuthoredHPQSPLike7(authoredhpqsplike7);
		mi.setAuthoredHPQSPLike1(authoredhpqsplike1);
		mi.setAuthoredHostnameLike30(authoredhostnamelike30);
		mi.setAuthoredHostnameLike7(authoredhostnamelike7);
		mi.setAuthoredHostnameLike1(authoredhostnamelike1);
		
		long threadviews30 = 0L;
		long threadviews7 = 0L;
		long threadviews1 = 0L;
		long separatedthreadviews30 = 0L;
		long separatedthreadviews7 = 0L;
		long separatedthreadviews1 = 0L;
		long sqspthreadviews30 = 0L;
		long sqspthreadviews7 = 0L;
		long sqspthreadviews1 = 0L;
		long loggedinthreadviews30 = 0L;
		long loggedinthreadviews7 = 0L;
		long loggedinthreadviews1 = 0L;
		long emptythreadviews30 = 0L;
		long emptythreadviews7 = 0L;
		long emptythreadviews1 = 0L;
		for (ThreadViewItem tvi : threadviewScanResult) { // this could get gigantic
			if(tvi.getMSFE() > msfe30) // we only care about tvis in the past 30 days. ignore everything else.
			{
				threadviews30++;
				if(tvi.getHostnameWasSeparated())
					separatedthreadviews30++;
				if(tvi.getHPHadSQSPS())
					sqspthreadviews30++;
				if(tvi.getWasLoggedIn())
					loggedinthreadviews30++;
				if(tvi.getWasEmpty())
					emptythreadviews30++;
				if(tvi.getMSFE() > msfe7) // we only care about tvis in the past 7 days. ignore everything else.
				{
					threadviews7++;
					if(tvi.getHostnameWasSeparated())
						separatedthreadviews7++;
					if(tvi.getHPHadSQSPS())
						sqspthreadviews7++;
					if(tvi.getWasLoggedIn())
						loggedinthreadviews7++;
					if(tvi.getWasEmpty())
						emptythreadviews7++;
					if(tvi.getMSFE() > msfe1) // we only care about tvis in the past 1 days. ignore everything else.
					{
						threadviews1++;
						if(tvi.getHostnameWasSeparated())
							separatedthreadviews1++;
						if(tvi.getHPHadSQSPS())
							sqspthreadviews1++;
						if(tvi.getWasLoggedIn())
							loggedinthreadviews1++;
						if(tvi.getWasEmpty())
							emptythreadviews1++;
					}
				}
			}
		}
		
		mi.setThreadViews30(threadviews30);
		mi.setThreadViews7(threadviews7);
		mi.setThreadViews1(threadviews1);
		mi.setSeparatedThreadViews30(separatedthreadviews30);
		mi.setSeparatedThreadViews7(separatedthreadviews7);
		mi.setSeparatedThreadViews1(separatedthreadviews1);
		mi.setSQSPThreadViews30(sqspthreadviews30);
		mi.setSQSPThreadViews7(sqspthreadviews7);
		mi.setSQSPThreadViews1(sqspthreadviews1);
		mi.setLoggedInThreadViews30(loggedinthreadviews30);
		mi.setLoggedInThreadViews7(loggedinthreadviews7);
		mi.setLoggedInThreadViews1(loggedinthreadviews1);
		mi.setEmptyThreadViews30(emptythreadviews30);
		mi.setEmptyThreadViews7(emptythreadviews7);
		mi.setEmptyThreadViews1(emptythreadviews1);
		
		/*
		 // key format:
		  * 						  V --- timeframe
		  * 					V----------------------- target
		  * 		  V----------------------- source or category
		  * "sci-join_email_facebook-30" 
		  *  ^--- "source"
		  *   ^------ "category" (or "s" for "source")
		  *    ^---------- "impressions" ("c" for "conversions")
		  * 
		 {
		 // ignoring specific sources/campaigns for now
		// 	"ssi-join_email-facebook-30": 18129037,
		// 	"ssc-join_email-facebook-30": 18129037,
		// 	"ssi-footer_prelaunch-facebook-7": 78123,
		// 	"ssc-footer_prelaunch-facebook-7": 1233,
		// 	"ssi-like_email-tumblr-30": 78123,
		// 	"ssc-like_email-tumblr-30": 1234,
		 		...
		 	"sci-email-facebook-30": 18129037,
		 	"scc-email-facebook-30": 15632,
		 	"sci-footer-facebook-7": 78123,
		 	"scc-footer-facebook-7": 78123,
		 	"sci-about-twitter-1": 78123,
		 	"scc-about-twitter-1": 1724,
		 		...
		 }
		 */
		
		JSONObject impression_jo = new JSONObject();
		try
		{
			//System.out.println("Entered impression try block");
			String currentsourcecategory = null;
			String currenttarget = null;
			String current_sci_key_string = null; // source category impressions
			String current_scc_key_string = null; // source category conversions
			long templong = 0L;
			for (ImpressionItem ii : impressionScanResult) { // this could get gigantic
				//System.out.println("Looping scan result");
				if(ii.getImpressionMSFE() > msfe30) // we only care about iis in the past 30 days. ignore everything else.
				{
					currentsourcecategory = ii.getSourceCategory();
					currenttarget = ii.getTarget();
					current_sci_key_string = "sci-" + currentsourcecategory + "-" + currenttarget + "-30";
					current_scc_key_string = "scc-" + currentsourcecategory + "-" + currenttarget + "-30";
					
					//impression
					if(!impression_jo.has(current_sci_key_string))
						impression_jo.put(current_sci_key_string, 0L);
					templong = impression_jo.getLong(current_sci_key_string) + 1;
					impression_jo.put(current_sci_key_string, templong);
					
					//conversion
					if(!impression_jo.has(current_scc_key_string))
						impression_jo.put(current_scc_key_string, 0L);
					
					//System.out.println("conversion_msfe=" + ii.getConversionMSFE());
					if(ii.getConversionMSFE() > 0) // if converted, increment
					{
						templong = impression_jo.getLong(current_scc_key_string) + 1;
						impression_jo.put(current_scc_key_string, templong);
					}
					
					if(ii.getImpressionMSFE() > msfe7) // we only care about iis in the past 7 days. ignore everything else.
					{
						current_sci_key_string = "sci-" + currentsourcecategory + "-" + currenttarget + "-7";
						current_scc_key_string = "scc-" + currentsourcecategory + "-" + currenttarget + "-7";
						
						//impression
						if(!impression_jo.has(current_sci_key_string))
							impression_jo.put(current_sci_key_string, 0L);
						templong = impression_jo.getLong(current_sci_key_string) + 1;
						impression_jo.put(current_sci_key_string, templong);
						
						//conversion
						if(!impression_jo.has(current_scc_key_string))
							impression_jo.put(current_scc_key_string, 0L);
						
						//System.out.println("conversion_msfe=" + ii.getConversionMSFE());
						if(ii.getConversionMSFE() > 0) // if converted, increment
						{
							templong = impression_jo.getLong(current_scc_key_string) + 1;
							impression_jo.put(current_scc_key_string, templong);
						}
						
						if(ii.getImpressionMSFE() > msfe1) // we only care about iis in the past 1 days. ignore everything else.
						{
							current_sci_key_string = "sci-" + currentsourcecategory + "-" + currenttarget + "-1";
							current_scc_key_string = "scc-" + currentsourcecategory + "-" + currenttarget + "-1";
							
							//impression
							if(!impression_jo.has(current_sci_key_string))
								impression_jo.put(current_sci_key_string, 0L);
							templong = impression_jo.getLong(current_sci_key_string) + 1;
							impression_jo.put(current_sci_key_string, templong);
							
							//conversion
							if(!impression_jo.has(current_scc_key_string))
								impression_jo.put(current_scc_key_string, 0L);
							
							//System.out.println("conversion_msfe=" + ii.getConversionMSFE());
							if(ii.getConversionMSFE() > 0) // if converted, increment
							{
								templong = impression_jo.getLong(current_scc_key_string) + 1;
								impression_jo.put(current_scc_key_string, templong);
							}
						}
					}
				}
			}
		}
		catch (JSONException jsone)
		{
			jsone.printStackTrace();
		}
			
		
		
		Calendar cal2 = Calendar.getInstance();
		long after = cal2.getTimeInMillis();
		long elapsed = after - before;
		mi.setProcessingTimeInMS(elapsed);

		System.out.println("StatsSnapshotter saving snapshot id=" + mi.getId());
		mapper.save(mi);
		//System.out.println("Setting impressions");
		mi.setImpressions(impression_jo, client);
		
		System.out.println("=== " + super.getId() +  " StatsSnapshotter Done.");
		return;
	}
	
	public static void main(String [] args)
	{
		AWSCredentials credentials;
		try {
			credentials = new PropertiesCredentials(StatsSnapshotter.class.getClassLoader().getResourceAsStream("AwsCredentials.properties"));
			AmazonDynamoDBClient client = new AmazonDynamoDBClient(credentials);
			WordsMapper mapper = new WordsMapper(client);
			DynamoDBMapperConfig dynamo_config = new DynamoDBMapperConfig(DynamoDBMapperConfig.ConsistentReads.EVENTUAL);
			StatsSnapshotter snapper = new StatsSnapshotter(mapper, dynamo_config, client);
			snapper.start();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		
	
	}
}