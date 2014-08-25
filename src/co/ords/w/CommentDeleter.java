package co.ords.w;

import java.util.Iterator;
import java.util.TreeSet;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;

public class CommentDeleter extends java.lang.Thread {

	private WordsMapper mapper;
	private DynamoDBMapperConfig dynamo_config;
	CommentItem commentitem;
	
	public void initialize()
	{
		dynamo_config = new DynamoDBMapperConfig(DynamoDBMapperConfig.ConsistentReads.EVENTUAL);
	}
	
	public CommentDeleter(CommentItem inc_ci, WordsMapper inc_mapper, DynamoDBMapperConfig inc_dynamo_config)
	{
		this.initialize();
		commentitem = inc_ci;
		mapper = inc_mapper;
		dynamo_config = inc_dynamo_config;
	}
	
	public void run()
	{
		System.out.println("=== " + super.getId() +  " Fired an CommentDeleter thread.");
		// 1. load the comment (done by caller)
				// 2. get the comment's children and call deleteCommentAndAllChildren() for each 
				// 3. get the comment's likes, 
				//  	3b. loop through these likes
				// 			3c. For each like, get the like's author
				// 				3d. Delete the like id from the author's likes_authored
				// 					3e. Delete the like itself
				// 4. get the comment's dislikes, 
				//  	4b. loop through these dislikes
				// 			4c. For each dislike, get the dislike's author
				// 				4d. Delete the dislike id from the author's dislikes_authored
				// 					4e. Delete the dislike itself
				// 5. Do any of the children, like or dislike ids appear in this user's activity_ids? If so, remove them and decrement notification_count
				// 6. delete comment_id from parent
				// 7. delete comment itself
		
		String comment_id = commentitem.getId();
		// 2. get the comment's children and call deleteCommentAndAllChildren() for each 
		TreeSet<CommentItem> comments = commentitem.getChildren(0, mapper, dynamo_config);
		if(comments != null)
		{
			//System.out.println("Removing children from comment=" + comment_id);
			Iterator<CommentItem> children_it = comments.iterator();
			CommentItem currentcomment = null;
			while(children_it.hasNext())
			{
				currentcomment = children_it.next();
				CommentDeleter cd = new CommentDeleter(currentcomment, mapper, dynamo_config);
				cd.start();
			}
		}
		// 3. get the comment's likes
		TreeSet<LikeItem> likes = commentitem.getLikes(mapper, dynamo_config); // get the likes set from the comment item
		if(likes != null)							// make sure it exists
		{
			//System.out.println("Removing likes from comment=" + comment_id);
			LikeItem current_likeitem = null;
			// 3b. loop through these likes
			Iterator<LikeItem> likes_it = likes.iterator(); 
			while(likes_it.hasNext())						// loop through the likes set
			{
				//current_like_id = likes_it.next();
				//current_likeitem = mapper.load(LikeItem.class, current_like_id, dynamo_config); // load the likeitem from the database
				current_likeitem = likes_it.next();
				if(current_likeitem != null)			// make sure it exists
				{
					mapper.delete(current_likeitem);																	// delete the comment item itself
				}
			}
		}
		// 4. get the comment's dislikes
		TreeSet<DislikeItem> dislikes = commentitem.getDislikes(mapper, dynamo_config); // get the likes set from the comment item
		if(dislikes != null)							// make sure it exists
		{
			//System.out.println("Removing dislikes from comment=" + comment_id);
			DislikeItem current_dislikeitem = null;
			// 3b. loop through these likes
			Iterator<DislikeItem> likes_it = dislikes.iterator(); 
			while(likes_it.hasNext())						// loop through the likes set
			{
				//current_like_id = likes_it.next();
				//current_likeitem = mapper.load(DislikeItem.class, current_like_id, dynamo_config); // load the likeitem from the database
				current_dislikeitem = likes_it.next();
				if(current_dislikeitem != null)			// make sure it exists
				{
					// 3e. delete the like itself
					mapper.delete(current_dislikeitem);																	// delete the comment item itself
				}
			}
		}	

		// 5. Clear out this user's activity_ids and set notification_count to 0. 
		// This is a very lazy brute-force way to do it, but meh. 
		// in the future, this should delete the current comment and all its likes/dislikes from activity_ids one by one and decrement notification count each time.
		UserItem comment_author = mapper.load( UserItem.class, commentitem.getAuthorId(), dynamo_config);
		comment_author.setActivityIds(null);
		comment_author.setNotificationCount(0);
		mapper.delete(commentitem);
		System.out.println("=== " + super.getId() +  " CommentDeleter Done.");
	}
	
	public static void main(String [] args)
	{
		//UserCalculator uc = new UserCalculator(); // author_id
		//uc.start();
	}
}