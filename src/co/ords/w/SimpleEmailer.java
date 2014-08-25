
package co.ords.w;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class SimpleEmailer extends java.lang.Thread {

	private String subject;
	private String body_text;
	private String body_html;
	private String to;
	private String from;
   
  public SimpleEmailer(String s, String bt, String bh, String t, String f)
  {
	  subject = s;
	  body_text = bt;
	  body_html = bh;
	  to = t;
	  from = f;
  }
  
  public void run()
  {
	  System.out.println("=== " + super.getId() +  " Fired a SimpleEmailer thread.");
	  Properties props = new Properties();
	  props.put("mail.smtp.auth", "true");
	  props.put("mail.smtp.starttls.enable", "true");
	  props.put("mail.smtp.host", "email-smtp.us-east-1.amazonaws.com");
	  props.put("mail.smtp.port", "25");
	  
	  Session session = Session.getInstance(props,
			  new javax.mail.Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() 
				{
					BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("AwsCredentials.properties")));
					String currentline = "";
					String ses_accessKey = ""; // same as wordscontroller, no ses admin priv
					String ses_secretKey = ""; // same as wordscontroller, no ses admin priv
					try {
						while((currentline = br.readLine()) != null)
						{
							if(currentline.indexOf("ses_accessKey=") != -1)
								ses_accessKey = currentline.substring(14);
							else if(currentline.indexOf("ses_secretKey=") != -1)
								ses_secretKey = currentline.substring(14);
						}
						return new PasswordAuthentication(ses_accessKey, ses_secretKey);
					}
					catch(IOException ioe)
					{
						ioe.printStackTrace();
						return null;
					}
				}
			});
	  try 
	  {
		  MimeMessage message = new MimeMessage(session);
	      message.setSubject(subject, "UTF-8");

	      try {
	    	  message.setFrom(new InternetAddress(from, "Words")); // this is the one exception to the all caps WORDS rule, so it doesn't get spam blocked
	      } catch (UnsupportedEncodingException e) {
	    	  // TODO Auto-generated catch block
	    	  e.printStackTrace();
	      }
	      message.setReplyTo(new Address[]{new InternetAddress("info@words4chrome.com")});
	      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
	      
	      // sends html alone
	      //message.setContent(body, "text/html");

	      // ALTERNATIVE TEXT/HTML CONTENT
	      MimeMultipart cover = new MimeMultipart("alternative");
	      MimeBodyPart html = new MimeBodyPart();
	      MimeBodyPart text = new MimeBodyPart();
	      cover.addBodyPart(text);
	      cover.addBodyPart(html);

	      message.setContent(cover);
	      text.setText(body_text,"utf-8"); // convert to plain text
	      html.setContent(body_html, "text/html; charset=\"utf-8\"");
	      
		  // SEND THE MESSAGE
	      message.setSentDate(new Date());
	      Transport.send(message);
	  } catch (MessagingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
	  }
	  System.out.println("=== " + super.getId() +  " SimpleEmailer Done.");
  }
  
  public static void main(String[] args) {
	  
  }
       
 
      
}

  