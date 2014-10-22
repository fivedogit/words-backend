package co.ords.w;

import java.util.Calendar;
import java.util.Iterator;
import java.util.TreeSet;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;

public class UserCalculator extends java.lang.Thread {

	private WordsMapper mapper;
	private DynamoDBMapperConfig dynamo_config;
	UserItem useritem;
	boolean valid = false;
	
	public void initialize()
	{
	}
	
	public UserCalculator(UserItem useritem, WordsMapper inc_mapper, DynamoDBMapperConfig inc_dynamo_config)
	{
		if(useritem == null)
			valid = false;
		else
		{	
			valid = true;
			this.initialize();
			this.useritem = useritem;
		}
		mapper = inc_mapper;
		dynamo_config = inc_dynamo_config;
	}
	
	public UserCalculator(String author_id)
	{
		this.initialize();
		useritem = mapper.load(UserItem.class, author_id, dynamo_config);
		if(useritem == null)
			valid = false;
		else
			valid = true;
	}
	
	private boolean getValid() // we have a valid UserItem
	{
		return valid;
	}
	
	public void run()
	{
		//System.out.println("=== " + super.getId() +  " Fired a UserCalculator thread.");
		if(!getValid())
			return;

		// FIRST, set the timestamp for this rating so this process it not fired repeatedly while calculating
		useritem.setRatingTS(System.currentTimeMillis()); //
		mapper.save(useritem);
		// then continue with the calculation
		
		double rating = 0;
		double rating_in_window = 0;

		long rating_window_mins = 10080; //set to sane value, then attempt to retrieve actual value //10080 = one week in minutes
		GlobalvarItem gvi = mapper.load(GlobalvarItem.class, "rating_window_mins", dynamo_config);
		if(gvi != null)
			rating_window_mins = gvi.getNumberValue();
		
		long cutoff_msfe = 0L;
		Calendar windowcal = Calendar.getInstance();
		windowcal.add(Calendar.MINUTE, ((int)rating_window_mins * -1));
		cutoff_msfe = windowcal.getTimeInMillis();
		
		/***
		 *     _    _ _____ _   _______ _____  _    _ 
		 *    | |  | |_   _| \ | |  _  \  _  || |  | |
		 *    | |  | | | | |  \| | | | | | | || |  | |
		 *    | |/\| | | | | . ` | | | | | | || |/\| |
		 *    \  /\  /_| |_| |\  | |/ /\ \_/ /\  /\  /
		 *     \/  \/ \___/\_| \_/___/  \___/  \/  \/ 
		 *                                            
		 *                                            
		 */
		
		//System.out.println("UserCalculator(): user=" + useritem.getScreennameLiteral() + " (window)");
		
		// whether or not this user has written any comments in the last 7 days (checked below), we want to know how many c/l/d he's written all time.
		int num_comments_authored_in_window = 0;
		int num_likes_authored_in_window = 0;
		int num_dislikes_authored_in_window = 0;
		int num_hostnamelikes_authored_in_window = 0;
		int num_hpqsplikes_authored_in_window = 0;
		
		// set number of likes/dislikes in window. Comments authored in window will be counted below.
		TreeSet<DislikeItem> ds = useritem.getDislikesAuthored((int)rating_window_mins, mapper, dynamo_config);
		if(ds != null)
			num_dislikes_authored_in_window = ds.size();
		TreeSet<LikeItem> ls = useritem.getLikesAuthored((int)rating_window_mins, mapper, dynamo_config);
		if(ls != null)
			num_likes_authored_in_window = ls.size();
		TreeSet<HostnameLikeItem> hostnamelikesset = useritem.getHostnameLikesAuthored((int)rating_window_mins, mapper, dynamo_config);
		if(hostnamelikesset != null)
			num_hostnamelikes_authored_in_window = hostnamelikesset.size();
		TreeSet<HPQSPLikeItem> hpqsplikesset = useritem.getHPQSPLikesAuthored((int)rating_window_mins, mapper, dynamo_config);
		if(hpqsplikesset != null)
			num_hpqsplikes_authored_in_window = hpqsplikesset.size();
		
		/***
		 *      ___   _      _     
		 *     / _ \ | |    | |    
		 *    / /_\ \| |    | |    
		 *    |  _  || |    | |    
		 *    | | | || |____| |____
		 *    \_| |_/\_____/\_____/
		 *                         
		 *                         
		 */
		
		//System.out.println("UserCalculator(): user=" + useritem.getScreennameLiteral() + " (all)");
		
		// Now get num comments all time
		int num_comments_authored = 0;
		int num_likes_authored = 0;
		int num_dislikes_authored = 0;
		int num_hostnamelikes_authored = 0;
		int num_hpqsplikes_authored = 0;
		
		TreeSet<CommentItem> c = useritem.getCommentsAuthored(0, mapper, dynamo_config);
		TreeSet<DislikeItem> d = useritem.getDislikesAuthored(0, mapper, dynamo_config);
		TreeSet<LikeItem> l = useritem.getLikesAuthored(0, mapper, dynamo_config);
		TreeSet<HostnameLikeItem> hostnamelikes_authored = useritem.getHostnameLikesAuthored(0, mapper, dynamo_config);
		TreeSet<HPQSPLikeItem> hpsqplikes_authored = useritem.getHPQSPLikesAuthored(0, mapper, dynamo_config);
		if(c != null)
			num_comments_authored = c.size();
		if(d != null)
			num_dislikes_authored = d.size();
		if(l != null)
			num_likes_authored = l.size();
		if(hostnamelikes_authored != null)
			num_hostnamelikes_authored = hostnamelikes_authored.size();
		if(hpsqplikes_authored != null)
			num_hpqsplikes_authored = hpsqplikes_authored.size();
		
		int cumulative_likes = 0;
		int cumulative_dislikes = 0;
		int cumulative_likes_in_window = 0;
		int cumulative_dislikes_in_window = 0;

		if(num_comments_authored > 0) // user has written no comments in window. Rating is zero.
		{
			Iterator<CommentItem> all_comments_it = c.descendingIterator();
			CommentItem currentcomment = null;
			TreeSet<LikeItem> currentlikes = null;
			TreeSet<DislikeItem> currentdislikes = null;
			while(all_comments_it.hasNext())
			{
				//System.out.println("current comment id=" + curr_str + "<");
				currentcomment = all_comments_it.next();
				currentlikes = currentcomment.getLikes(mapper, dynamo_config);
				currentdislikes = currentcomment.getDislikes(mapper, dynamo_config);
				
				if(currentcomment.getMSFE() > cutoff_msfe) // this comment is in the window
				{
					num_comments_authored_in_window++;
					if(currentlikes != null)
					{	
						cumulative_likes_in_window += currentlikes.size();
						cumulative_likes += currentlikes.size();
					}
					
					if(currentdislikes != null)
					{
						cumulative_dislikes_in_window += currentdislikes.size(); 
						cumulative_dislikes += currentdislikes.size();
					}
				}
				else // this comment is outside the window
				{
					if(currentlikes != null)
						cumulative_likes += currentlikes.size();
					
					if(currentdislikes != null)
						cumulative_dislikes += currentdislikes.size();
				}
			}
			
			// computing user rating:
			// First, if the user has less than 10 likes/dislikes over the time period, just give them a 0. 
			// 		This is necessary since a rating of 1 dislike and zero likes would give the user a -5 rating right off the bat. Hidden to almost everyone immediately.
			// Second, if the user has zero likes or zero dislikes, don't try division. Just set them to 5 or -5 and move on.
			// Third, calc the score by dividing likes/dislikes. If > 1 and < 5, use that as the score.
			// Fourth, if < 1, then invert the equation (dislikes/likes). If > 1 and < 5, then use that as the score, + flipped to negative.
			/***
			 *     _    _ _____ _   _______ _____  _    _ 
			 *    | |  | |_   _| \ | |  _  \  _  || |  | |
			 *    | |  | | | | |  \| | | | | | | || |  | |
			 *    | |/\| | | | | . ` | | | | | | || |/\| |
			 *    \  /\  /_| |_| |\  | |/ /\ \_/ /\  /\  /
			 *     \/  \/ \___/\_| \_/___/  \___/  \/  \/ 
			 *                                            
			 *                                            
			 */
			if(cumulative_dislikes_in_window + cumulative_likes_in_window < 10)
				rating_in_window = 0;
			else if(cumulative_dislikes_in_window == 0 && cumulative_likes_in_window > 0)
				rating_in_window = 5;
			else if(cumulative_likes_in_window == 0 && cumulative_dislikes_in_window > 0)
				rating_in_window = -5;
			else
			{	
				double cumulative_likes_in_window_double = (double)cumulative_likes_in_window;
				double cumulative_dislikes_in_window_double = (double)cumulative_dislikes_in_window;
				if(cumulative_likes_in_window_double > cumulative_dislikes_in_window) // if more likes_in_window than dislikes_in_window, like/dislike will always be >= 1, so we subtract 1 to get a rating_in_window from 0 upward
				{	
					rating_in_window = cumulative_likes_in_window_double/cumulative_dislikes_in_window_double - 1;
					rating_in_window = rating_in_window * 10;
					rating_in_window = (double)((int) rating_in_window);
					rating_in_window = rating_in_window / 10;
					if(rating_in_window > 5)
						rating_in_window = 5;
				}
				else // if more likes_in_window than dislikes_in_window, dislike/like will always be >= 1, so we subtract 1 to get a rating_in_window from 0 upward (which we then turn negative)
				{
					rating_in_window = (cumulative_dislikes_in_window_double/cumulative_likes_in_window_double - 1) * -1;
					rating_in_window = rating_in_window * 10;
					rating_in_window = (double)((int) rating_in_window);
					rating_in_window = rating_in_window / 10;
					if(rating_in_window < -5)
						rating_in_window = -5;
				}
			}
			
			/***
			 *      ___   _      _     
			 *     / _ \ | |    | |    
			 *    / /_\ \| |    | |    
			 *    |  _  || |    | |    
			 *    | | | || |____| |____
			 *    \_| |_/\_____/\_____/
			 *                         
			 *                         
			 */
			if(cumulative_dislikes + cumulative_likes < 10)
				rating = 0;
			else if(cumulative_dislikes == 0 && cumulative_likes > 0)
				rating = 5;
			else if(cumulative_likes == 0 && cumulative_dislikes > 0)
				rating = -5;
			else
			{	
				double cumulative_likes_double = (double)cumulative_likes;
				double cumulative_dislikes_double = (double)cumulative_dislikes;
				if(cumulative_likes_double > cumulative_dislikes) // if more likes than dislikes, like/dislike will always be >= 1, so we subtract 1 to get a rating from 0 upward
				{	
					rating = cumulative_likes_double/cumulative_dislikes_double - 1;
					rating = rating * 10;
					rating = (double)((int) rating);
					rating = rating / 10;
					if(rating > 5)
						rating = 5;
				}
				else // if more likes than dislikes, dislike/like will always be >= 1, so we subtract 1 to get a rating from 0 upward (which we then turn negative)
				{
					rating = (cumulative_dislikes_double/cumulative_likes_double - 1) * -1;
					rating = rating * 10;
					rating = (double)((int) rating);
					rating = rating / 10;
					if(rating < -5)
						rating = -5;
				}
			}
			
		}
	
		//System.out.println("UserItem.calcRating() likes=" + cumulative_likes + " dislikes=" + cumulative_dislikes + " rating=" + rating);
		useritem.setUp(cumulative_likes); //
		useritem.setUpInWindow(cumulative_likes_in_window); // 
		
		useritem.setDown(cumulative_dislikes); // 
		useritem.setDownInWindow(cumulative_dislikes_in_window); // 
		
		useritem.setRating(rating); // 
		useritem.setRatingInWindow(rating_in_window); // 
		
		//System.out.println("UC: Setting #c window=" + num_comments_authored_in_window + " #c all=" + num_comments_authored);
		useritem.setNumCommentsAuthored(num_comments_authored); // 
		useritem.setNumCommentsAuthoredInWindow(num_comments_authored_in_window); // 
		
		useritem.setNumLikesAuthored(num_likes_authored);
		useritem.setNumLikesAuthoredInWindow(num_likes_authored_in_window);
		
		useritem.setNumDislikesAuthored(num_dislikes_authored);
		useritem.setNumDislikesAuthoredInWindow(num_dislikes_authored_in_window);
		
		useritem.setNumHostnameLikesAuthored(num_hostnamelikes_authored);
		useritem.setNumHostnameLikesAuthoredInWindow(num_hostnamelikes_authored_in_window);
		
		useritem.setNumHPQSPLikesAuthored(num_hpqsplikes_authored);
		useritem.setNumHPQSPLikesAuthoredInWindow(num_hpqsplikes_authored_in_window);
		
		useritem.setRatingTS(System.currentTimeMillis()); //
		mapper.save(useritem);
		
		double hideall_threshold = -5.0;
		GlobalvarItem gvi2 = mapper.load(GlobalvarItem.class, "hideall_threshold", dynamo_config);
		if(gvi2 != null)
			hideall_threshold = gvi2.getNumberValue();
		
		// each time a calc is done, check to see if this user's shit should be hidden forever
		if(rating_in_window <= hideall_threshold)
		{
			//System.out.println("******************* USER " + useritem.getScreenname() + " HAS GONE UNDER THRESHOLD FOR SUCKING AT COMMENTING. Was previously above? ********");
			/*if(oldrating > hideall_threshold)
			{	
				//System.out.println("******************* USER " + useritem.getScreenname() + " Yes. Hiding all. ********");
				useritem.hideAllComments(mapper, dynamo_config);
			}
			else
			{ 
				//System.out.println("******************* USER " + useritem.getScreenname() + " No. Everything was already hidden and user should have been silenced since then. ********");
			}*/
			// NOTE: if the above is uncommented, then everything should work fine UNLESS the hideall_threshold gets changed.
			// then if the user is above the old thresh but below the new one, their stuff will never get hidden because the above assumes
			// that all that stuff was hidden already. Not good.
			
			// instead we can just hide the user's stuff as soon as we hit this. 2 reasons
			// 1. If the person's stuff is all hidden, then it should be impossible for them to continually get downvoted
			// 2. the calc TTL should prevent this from being a major strain on resources.
			//System.out.println("******************* USER " + useritem.getScreennameLiteral() + " dipped below hideall_threshold. Hiding all. ********");
			useritem.hideAllComments(mapper, dynamo_config);
		}
		//System.out.println("=== " + super.getId() +  " UserCalculator Done.");
	}
	
	public static void main(String [] args)
	{
		UserCalculator uc = new UserCalculator(""); // author_id
		uc.start();
	}
}