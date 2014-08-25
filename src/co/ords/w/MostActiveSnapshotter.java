package co.ords.w;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

public class MostActiveSnapshotter extends java.lang.Thread {

	private WordsMapper mapper;
	private DynamoDBMapperConfig dynamo_config;
	private int num_hours;
	
	public void initialize()
	{
	}
	
	public MostActiveSnapshotter(int inc_num_hours, WordsMapper inc_mapper, DynamoDBMapperConfig inc_dynamo_config)
	{
		this.initialize();
		mapper = inc_mapper;
		dynamo_config = inc_dynamo_config;
		num_hours = inc_num_hours;
	}
	
	public void run()
	{
		System.out.println("=== " + super.getId() +  " Fired an MostActiveSnapshotter (web-wide, no url) thread.");
		try
		{
			long cutoff_in_msfe = 0L;
			
			// get the MSFE cutoffs
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.HOUR, num_hours * -1);
			cutoff_in_msfe = cal.getTimeInMillis();
			
			int totalitems = 0;
			//System.out.println(48 + ": " + cutoff_in_msfe);
				
			// Scan the trending table
			DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
			List<CommentItem> scanResult = mapper.scan(CommentItem.class, scanExpression, dynamo_config);
			totalitems = scanResult.size();
			// build a map that looks like this:
			// hpqsp key: { 
			//			"page_title": "blah",
			//			"url_when_created": "blah"
			//			"count": x
			//		}
				
			Map<String, SortableJSONObject> hpqsps = new HashMap<String, SortableJSONObject>();
			SortableJSONObject temp_jo = null;
			int temp_count = 0;
			for (CommentItem ti : scanResult) {
				//System.out.println(ti.getURLWhenCreated() + " looping");
				if(ti.getMSFE() > cutoff_in_msfe)
				{
					//System.out.println(ti.getURLWhenCreated() + " within cutoff");
					if(hpqsps.containsKey(ti.getHPQSP())) // if the key already exists, just up the count by 1
					{
						//System.out.println(ti.getURLWhenCreated() + " already contains key");
						temp_jo = hpqsps.get(ti.getHPQSP());
						temp_count = temp_jo.getInt("count");
						temp_jo.put("count", (temp_count + 1));
						hpqsps.put(ti.getHPQSP(), temp_jo);
					}
					else // if it doesn't, then create a new jsonobject on that key with a count = 1
					{
						//System.out.println(ti.getURLWhenCreated() + " doesn't contain key, creating");
						temp_jo = new SortableJSONObject();
						temp_jo.put("count", 1);
						temp_jo.put("page_title", ti.getPageTitle());
						if(ti.getURLWhenCreated().startsWith("https://"))
						{
							if(ti.getHPQSP().endsWith("?"))
								temp_jo.put("pseudo_url", "https://" + ti.getHPQSP().substring(0,ti.getHPQSP().length()-1));
							else
								temp_jo.put("pseudo_url", "https://" + ti.getHPQSP());
						}
						else
						{
							if(ti.getHPQSP().endsWith("?"))
								temp_jo.put("pseudo_url", "http://" + ti.getHPQSP().substring(0,ti.getHPQSP().length()-1));
							else
								temp_jo.put("pseudo_url", "http://" + ti.getHPQSP());
						}
						//temp_jo.put("url_when_created", ti.getURLWhenCreated()); // insecure to return this?
						hpqsps.put(ti.getHPQSP(), temp_jo);
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
			while(it.hasNext() && i < 25)
			{
				return_ja.put(it.next());
				i++;
			}
			
			// create these new each time bc we don't care what, if anything, was there before
				
			GlobalvarItem newgvi = new GlobalvarItem();
			newgvi.setName("most_active_snapshot");
			JSONObject jsonresponse = new JSONObject();
			jsonresponse.put("response_status", "success");
			jsonresponse.put("trendingactivity_ja", return_ja);
			jsonresponse.put("num_hours", num_hours); // pass this back to the frontend so we can show the user how many hours (dicated by global db variable) we got
			jsonresponse.put("items_processed", totalitems);
			newgvi.setStringValue(jsonresponse.toString());
			mapper.save(newgvi);
			
			long now = System.currentTimeMillis();
			GlobalvarItem new_ts_gvi = new GlobalvarItem();
			new_ts_gvi.setName("most_active_snapshot_ts");
			new_ts_gvi.setNumberValue(now);
			mapper.save(new_ts_gvi);
			
		} catch (JSONException e) {
			e.printStackTrace();
		}
		System.out.println("=== " + super.getId() +  " MostActiveSnapshotter Done.");
		return;
	}
	
	public static void main(String [] args)
	{
		//UserCalculator uc = new UserCalculator(); // author_id
		//uc.start();
	}
}