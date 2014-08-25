package co.ords.w;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

public class SortableJSONObject extends JSONObject implements Comparable<SortableJSONObject> {

	@DynamoDBIgnore
	public int compareTo(SortableJSONObject o) // this makes more recent comments come first
	{
	    Integer othercount;
		try {
			othercount = ((SortableJSONObject)o).getInt("count");
			int x = othercount.compareTo(getInt("count"));
			if(x >= 0) // this is to prevent equals
				return 1;
			else
				return -1;
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return -1;
	}
	
}
