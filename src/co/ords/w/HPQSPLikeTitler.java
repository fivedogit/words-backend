package co.ords.w;

import java.io.IOException;
import java.io.StringReader;

import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;

public class HPQSPLikeTitler extends java.lang.Thread {

	private WordsMapper mapper;
	private DynamoDBMapperConfig dynamo_config;
	HPQSPLikeItem i;
	String url;
	
	public void initialize()
	{
	}
	
	public HPQSPLikeTitler(HPQSPLikeItem inc_i, String inc_url, WordsMapper inc_mapper, DynamoDBMapperConfig inc_dynamo_config)
	{
		this.initialize();
		i = inc_i;
		url = inc_url;
		mapper = inc_mapper;
		dynamo_config = inc_dynamo_config;
	}
	
	public void run()
	{
		System.out.println("=== " + super.getId() +  " Fired a HPQSPLikeTitler thread.");
		
		String title = "";
        HttpClient httpclient = new DefaultHttpClient();
        try {
            HttpGet httpget = new HttpGet(url);

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
                    //System.out.println("HTMLDocument Title: " + title);
                    i.setPageTitle(title);
                    mapper.save(i, dynamo_config);
                } catch (Exception e) 
                {
                    System.err.println("Unexpected " + e + " thrown");
                }
        } 
        catch(ClientProtocolException cpe)
        {
        	System.err.println(cpe.getMessage());
        }
        catch(IOException ioe)
        {
        	System.err.println(ioe.getMessage());
        }
        finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            httpclient.getConnectionManager().shutdown();
        }
		System.out.println("=== " + super.getId() +  " HPQSPLikeTitler Done.");
	}
	
	public static void main(String [] args)
	{

	}
}