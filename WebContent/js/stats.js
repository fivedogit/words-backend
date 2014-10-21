var devel = true; 
var endpoint = "https://w.ords.co/endpoint";
if(devel === true)
	endpoint = "http://localhost:8080/words/endpoint";

var docCookies = {
		  getItem: function (sKey) {
		    if (!sKey || !this.hasItem(sKey)) { return null; }
		    return unescape(document.cookie.replace(new RegExp("(?:^|.*;\\s*)" + escape(sKey).replace(/[\-\.\+\*]/g, "\\$&") + "\\s*\\=\\s*((?:[^;](?!;))*[^;]?).*"), "$1"));
		  },
		  setItem: function (sKey, sValue, vEnd, sPath, sDomain, bSecure) {
		    if (!sKey || /^(?:expires|max\-age|path|domain|secure)$/i.test(sKey)) { return; }
		    var sExpires = "";
		    if (vEnd) {
		      switch (vEnd.constructor) {
		        case Number:
		          sExpires = vEnd === Infinity ? "; expires=Tue, 19 Jan 2038 03:14:07 GMT" : "; max-age=" + vEnd;
		          break;
		        case String:
		          sExpires = "; expires=" + vEnd;
		          break;
		        case Date:
		          sExpires = "; expires=" + vEnd.toGMTString();
		          break;
		      }
		    }
		    document.cookie = escape(sKey) + "=" + escape(sValue) + sExpires + (sDomain ? "; domain=" + sDomain : "") + (sPath ? "; path=" + sPath : "") + (bSecure ? "; secure" : "");
		  },
		  removeItem: function (sKey, sPath) {
		    if (!sKey || !this.hasItem(sKey)) { return; }
		    document.cookie = escape(sKey) + "=; expires=Thu, 01 Jan 1970 00:00:00 GMT" + (sPath ? "; path=" + sPath : "");
		  },
		  hasItem: function (sKey) {
		    return (new RegExp("(?:^|;\\s*)" + escape(sKey).replace(/[\-\.\+\*]/g, "\\$&") + "\\s*\\=")).test(document.cookie);
		  }
		};


$(document).ready(function () 
{	
	/*var days = 30;
	if(docCookies.getItem("days") != null)
	{
		days = docCookies.getItem("days");
	}
	
	$("#days_button").click(function() {
		docCookies.setItem("days", $("#days_input").val(), 3000000);
		window.location.reload();
	});
	
	
	
	if(docCookies.getItem("passkey") == null)
	{
		var passkeyform = "";
		passkeyform = "<input type=text id=\"passkeinput\" size=30><button id=\"passkesubmit_button\">Submit</button>";
		$("#topdiv").html(passkeyform);
		$("#passkesubmit_button").click(function() {
			docCookies.setItem("passkey", $("#passkeinput").val(), 3000000);
			window.location.reload();
		});
	}
	else
	{
		var getfieldsform = "";
		getfieldsform = "<button id=\"show_impression_fields_link\">Show impression fields</button>";
		$("#topdiv").html(getfieldsform);
		$("#show_impression_fields_link").click(function() {
			$.ajax({
				type: 'GET',
				url: endpoint,
				data: {
		            method: "getMetricData",
		            passkey: docCookies.getItem("passkey"),
		            days: days
				},
		        dataType: 'json',
		        async: true,
		        success: function (data, status) {
		        	if (data.response_status == "error")
		        	{
		        		alert(data.message);
		        	}
		        	else if (data.response_status == "success")
		        	{
		        		var str = "";
		        		var keys = [];
		        		for(var k in metric_data) keys.push(k);
		        		keys.sort();
		        		var x = 0;
		        		while(x < keys.length)
		        		{
		        			str = str + "<br>" + keys[x];
		        			x++;
		        		}
		        		$("#main_div").html(str);
		        	}
		        }
			});
		});
			*/
		var metric_data;
		$.ajax({
			type: 'GET',
			url: endpoint,
			data: {
	            method: "getMetricData",
	           // passkey: docCookies.getItem("passkey"),
	           // days: days
			},
	        dataType: 'json',
	        async: true,
	        success: function (data, status) {
	        	if (data.response_status == "error")
	        	{
	        		alert(data.message);
	        	}
	        	else if (data.response_status == "success")
	        	{
	        		$("#topdiv").text("from_cache=" + data.from_cache + " at " + data.timestamp_hr);
	        		
	        		metric_data = data.metric_data;
	        		var keys = [];
	        		for(var k in metric_data) keys.push(k);
	        		
	        		//$("#topdiv").text(JSON.stringify(keys));
	        		var metric_data_processed = {};
	        		for (var i=0; i < keys.length; i++)
	        		{ 
	        			metric_data_processed[keys[i]] = [];
	        		}
	        		
	        		//alert(JSON.stringify(metric_data_processed));
	        		for (var y=0; y < keys.length; y++)
	        		{ 
	        			for(var x=0; x < metric_data.msfe.length; x++)
	        			{	
	        				metric_data_processed[keys[y]].push([metric_data.msfe[x],metric_data[keys[y]][x]]);
	        			}
	        		}
	        		
	        		var threadviews = $.jqplot('threadviews', [metric_data_processed["threadviews"]],
	        		{		
	        			title:'Cumulative thread views - User clicked activation button and saw a thread',
	        			axes: {
	        				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	            	        yaxis: {
	            	        	min: 0
	            	        }
	        			},
	        			legend:{
	        				show:true,
	        				location:"nw",
	        				labels: ["threadviews_cumulative"]
	        			},
	        			seriesColors: ["blue"],
	        		});
	        		
	        		var threadretrievals = $.jqplot('threadretrievals', [metric_data_processed["threadretrievals"]],
	    	        		{		
	    	        			title:'Cumulative thread retrievals - A thread was retrieved from the database (on tab change/update), but not necessarily viewed',
	    	        			axes: {
	    	        				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	    	            	        yaxis: {
	    	            	        	min: 0
	    	            	        }
	    	        			},
	    	        			legend:{
	    	        				show:true,
	    	        				location:"nw",
	    	        				labels: ["threadretrievals_cumulative"]
	    	        			},
	    	        			seriesColors: [ "red"],
	    	        		});
	        		
	        		var loggedinthreadviews = $.jqplot('loggedinthreadviews', [metric_data_processed["threadviews30"],metric_data_processed["threadviews7"],metric_data_processed["threadviews1"],
	        																metric_data_processed["loggedinthreadviews30"],metric_data_processed["loggedinthreadviews7"],metric_data_processed["loggedinthreadviews1"]],
	        		{		
	        			title:'ThreadViews - Total threadviews (all vs logged-in users only)',
	        			axes: {
	        				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	            	        yaxis: {
	            	        	min: 0,
	            	        }
	        			},
	        			legend:{
	        				show:true,
	        				location:"nw",
	        				labels: ["tviews30","tviews7","tviews1","loggedinviews30","loggedinviews7","loggedinviews1"]
	        			},
	        			seriesColors: [ "darkred", "red", "pink", "darkblue", "blue", "lightblue"],
	        		});
	        		
	        		var g_vs_fb = $.jqplot('g_vs_fb', [metric_data_processed["google_as_last_login_type"],metric_data_processed["facebook_as_last_login_type"],metric_data_processed["native_as_last_login_type"]], 
     	        				 {		
     	           			title:'Users logging in with Google vs Facebook vs Native',
     	           			axes: {
     	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
     	               	        yaxis: {
     	               	        	min: 0,
     	               	        }
     	           			},
                    	        legend:{
     	        				show:true,
     	        				location:"nw",
     	        				labels: ["google","facebook", "native"]
     	        			},
     	        			seriesColors: [  "red", "blue", "black"],
     	           		});
	        		
	        		var users = $.jqplot('users', [metric_data_processed["registered_users"]],
	    	        		{		
	    	        			title:'Registered users, cumulative',
	    	        			axes: {
	    	        				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	    	            	        yaxis: {
	    	            	        	min: 0
	    	            	        }
	    	        			},
	    	        			legend:{
	    	        				show:true,
	    	        				location:"nw",
	    	        				labels: ["registered_users"]
	    	        			},
	    	        			seriesColors: ["blue"],
	    	        		});
	    	        		
	        		var giveawayeligible_users = $.jqplot('giveawayeligible_users', [metric_data_processed["registered_commented_confirmed"]],
	    	        		{		
	    	        			title:'Users eligible for giveaways (Registered + commented + email confirmed)',
	    	        			axes: {
	    	        				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	    	            	        yaxis: {
	    	            	        	min: 0
	    	            	        }
	    	        			},
	    	        			legend:{
	    	        				show:true,
	    	        				location:"nw",
	    	        				labels: ["giveaway-eligible_users"]
	    	        			},
	    	        			seriesColors: ["blue"],
	    	        		});
	        		
	        		var users_comments = $.jqplot('users_comments', [metric_data_processed["commented30"],metric_data_processed["commented7"],metric_data_processed["commented1"]], 
	        		{		
	        			title:'User activity - Users that have written a comment in the past X days',
	        			axes: {
	        				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	        				yaxis: {
	        					min: 0
	        					//,max: active_max
	        				}
	        			},
	        			legend:{
	        				show:true,
	        				location:"nw",
	        				labels: ["commented30","commented7","commented1"]
	        			},
	        			seriesColors: ["darkblue", "blue", "lightblue"],
	        		});
	        		
	        		var users_likes = $.jqplot('users_likes', [metric_data_processed["authoredlike30"],metric_data_processed["authoredlike7"],metric_data_processed["authoredlike1"]], 
		{		
			title:'User activity - Users that have liked something in the past X days',
			axes: {
				xaxis:{renderer:$.jqplot.DateAxisRenderer},
				yaxis: {
					min: 0
					//,max: active_max
				}
			},
			legend:{
				show:true,
				location:"nw",
				labels: ["like30","like7","like1"]
			},
			seriesColors: ["darkgreen", "green","lightgreen"],
		});
	        		
	        		var users_dislikes = $.jqplot('users_dislikes', [metric_data_processed["authoreddislike30"],metric_data_processed["authoreddislike7"],metric_data_processed["authoreddislike1"]], 
		{		
			title:'User activity -  Users that have disliked something in the past X days',
			axes: {
				xaxis:{renderer:$.jqplot.DateAxisRenderer},
				yaxis: {
					min: 0
					//,max: active_max
				}
			},
			legend:{
				show:true,
				location:"nw",
				labels: ["dislike30","dislike7","dislike1"]
			},
			seriesColors: [ "darkred", "red", "pink"],
		});
	        		
	        		var users_hosthpqsp = $.jqplot('users_hosthpqsp', [metric_data_processed["authoredhostnamelike30"],metric_data_processed["authoredhostnamelike7"],metric_data_processed["authoredhostnamelike1"],
	        															metric_data_processed["authoredhpqsplike30"],metric_data_processed["authoredhpqsplike7"],metric_data_processed["authoredhpqsplike1"]], 
	  	        	{		
	        			title:'User activity - Users that have liked a hostname (whole site) vs users that have liked a hpqsp (single page) in the past X days',
	        			axes: {
	        				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	        				yaxis: {
	        					min: 0,
	        					//,max: active_max
	        				}
	        			},
	        			legend:{
	        				show:true,
	        				location:"nw",
	        				labels: ["hostnamelike30","hostnamelike7","hostnamelike1","hpqsplike30","hpqsplike7","hpqsplike1"]
	        			},
	        			seriesColors: [ "darkred", "red", "pink", "darkblue", "blue", "lightblue"],
	  	        	});
	        		
	        		var sc_footer_facebookshare = $.jqplot('sc-footer-facebookshare', [metric_data_processed["sci-footer-facebookshare-30"],metric_data_processed["sci-footer-facebookshare-7"],metric_data_processed["sci-footer-facebookshare-1"],
														metric_data_processed["scc-footer-facebookshare-30"],metric_data_processed["scc-footer-facebookshare-7"],metric_data_processed["scc-footer-facebookshare-1"]], 
	        				 {		
	           			title:'sc-footer-facebookshare (category) - "Share to Facebook" impressions vs conversions (from the footer)',
	           			axes: {
	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	               	        yaxis: {
	               	        	min: 0,
	               	        }
	           			},
               	        legend:{
	        				show:true,
	        				location:"nw",
	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
	        			},
	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
	           		});
	        		
	        		var sc_footer_twittershare = $.jqplot('sc-footer-twittershare', [metric_data_processed["sci-footer-twittershare-30"],metric_data_processed["sci-footer-twittershare-7"],metric_data_processed["sci-footer-twittershare-1"],
	     														metric_data_processed["scc-footer-twittershare-30"],metric_data_processed["scc-footer-twittershare-7"],metric_data_processed["scc-footer-twittershare-1"]], 
	     	        				 {		
	     	           			title:'sc-footer-twittershare (category) - "Share to Twitter" impressions vs conversions (from the footer)',
	     	           			axes: {
	     	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	     	               	        yaxis: {
	     	               	        	min: 0,
	     	               	        }
	     	           			},
	                    	        legend:{
	     	        				show:true,
	     	        				location:"nw",
	     	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
	     	        			},
	     	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
	     	           		});
	        		
	        		var sc_footer_googleplusshare = $.jqplot('sc-footer-googleplusshare', [metric_data_processed["sci-footer-googleplusshare-30"],metric_data_processed["sci-footer-googleplusshare-7"],metric_data_processed["sci-footer-googleplusshare-1"],
	       	     														metric_data_processed["scc-footer-googleplusshare-30"],metric_data_processed["scc-footer-googleplusshare-7"],metric_data_processed["scc-footer-googleplusshare-1"]], 
	       	     	        				 {		
	       	     	           			title:'sc-footer-googleplusshare (category) - "Share to G+" impressions vs conversions (from the footer)',
	       	     	           			axes: {
	       	     	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	       	     	               	        yaxis: {
	       	     	               	        	min: 0,
	       	     	               	        }
	       	     	           			},
	       	                    	        legend:{
	       	     	        				show:true,
	       	     	        				location:"nw",
	       	     	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
	       	     	        			},
	       	     	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
	       	     	           		});
	        		
	        		var sc_footer_tumblrshare = $.jqplot('sc-footer-tumblrshare', [metric_data_processed["sci-footer-tumblrshare-30"],metric_data_processed["sci-footer-tumblrshare-7"],metric_data_processed["sci-footer-tumblrshare-1"],
	       	     														metric_data_processed["scc-footer-tumblrshare-30"],metric_data_processed["scc-footer-tumblrshare-7"],metric_data_processed["scc-footer-tumblrshare-1"]], 
	       	     	        				 {		
	       	     	           			title:'sc-footer-tumblrshare (category) - "Share to Tumblr" impressions vs conversions (from the footer)',
	       	     	           			axes: {
	       	     	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	       	     	               	        yaxis: {
	       	     	               	        	min: 0,
	       	     	               	        }
	       	     	           			},
	       	                    	        legend:{
	       	     	        				show:true,
	       	     	        				location:"nw",
	       	     	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
	       	     	        			},
	       	     	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
	       	     	           		});
	        		
	        		
	        		var sc_footer_cws = $.jqplot('sc-footer-cws', [metric_data_processed["sci-footer-cws-30"],metric_data_processed["sci-footer-cws-7"],metric_data_processed["sci-footer-cws-1"],
	       	     														metric_data_processed["scc-footer-cws-30"],metric_data_processed["scc-footer-cws-7"],metric_data_processed["scc-footer-cws-1"]], 
	       	     	        				 {		
	       	     	           			title:'sc-footer-cws (category) - "Rate 5 stars in CWS" impressions vs conversions (from the footer)',
	       	     	           			axes: {
	       	     	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	       	     	               	        yaxis: {
	       	     	               	        	min: 0,
	       	     	               	        }
	       	     	           			},
	       	                    	        legend:{
	       	     	        				show:true,
	       	     	        				location:"nw",
	       	     	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
	       	     	        			},
	       	     	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
	       	     	           		});
	        		
	       	     	           		
	        		var sc_about_facebookshare = $.jqplot('sc-about-facebookshare', [metric_data_processed["sci-about-facebookshare-30"],metric_data_processed["sci-about-facebookshare-7"],metric_data_processed["sci-about-facebookshare-1"],
	        		           														metric_data_processed["scc-about-facebookshare-30"],metric_data_processed["scc-about-facebookshare-7"],metric_data_processed["scc-about-facebookshare-1"]], 
	        		           	        				 {		
	        		           	           			title:'sc-about-facebookshare (category) - "Share to Facebook" impressions vs conversions (from the about tab)',
	        		           	           			axes: {
	        		           	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	        		           	               	        yaxis: {
	        		           	               	        	min: 0,
	        		           	               	        }
	        		           	           			},
	        		                          	        legend:{
	        		           	        				show:true,
	        		           	        				location:"nw",
	        		           	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
	        		           	        			},
	        		           	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
	        		           	           		});
	        		           	        		
	        		           	        		var sc_about_twittershare = $.jqplot('sc-about-twittershare', [metric_data_processed["sci-about-twittershare-30"],metric_data_processed["sci-about-twittershare-7"],metric_data_processed["sci-about-twittershare-1"],
	        		           	     														metric_data_processed["scc-about-twittershare-30"],metric_data_processed["scc-about-twittershare-7"],metric_data_processed["scc-about-twittershare-1"]], 
	        		           	     	        				 {		
	        		           	     	           			title:'sc-about-twittershare (category) - "Share to Twitter" impressions vs conversions (from the about tab)',
	        		           	     	           			axes: {
	        		           	     	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	        		           	     	               	        yaxis: {
	        		           	     	               	        	min: 0,
	        		           	     	               	        }
	        		           	     	           			},
	        		           	                    	        legend:{
	        		           	     	        				show:true,
	        		           	     	        				location:"nw",
	        		           	     	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
	        		           	     	        			},
	        		           	     	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
	        		           	     	           		});
	        		           	        		
	        		           	        		var sc_about_googleplusshare = $.jqplot('sc-about-googleplusshare', [metric_data_processed["sci-about-googleplusshare-30"],metric_data_processed["sci-about-googleplusshare-7"],metric_data_processed["sci-about-googleplusshare-1"],
	        		           	       	     														metric_data_processed["scc-about-googleplusshare-30"],metric_data_processed["scc-about-googleplusshare-7"],metric_data_processed["scc-about-googleplusshare-1"]], 
	        		           	       	     	        				 {		
	        		           	       	     	           			title:'sc-about-googleplusshare (category) - "Share to G+" impressions vs conversions (from the about tab in the overlay)',
	        		           	       	     	           			axes: {
	        		           	       	     	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	        		           	       	     	               	        yaxis: {
	        		           	       	     	               	        	min: 0,
	        		           	       	     	               	        }
	        		           	       	     	           			},
	        		           	       	                    	        legend:{
	        		           	       	     	        				show:true,
	        		           	       	     	        				location:"nw",
	        		           	       	     	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
	        		           	       	     	        			},
	        		           	       	     	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
	        		           	       	     	           		});
	        		           	        		
	        		           	        		var sc_about_tumblrshare = $.jqplot('sc-about-tumblrshare', [metric_data_processed["sci-about-tumblrshare-30"],metric_data_processed["sci-about-tumblrshare-7"],metric_data_processed["sci-about-tumblrshare-1"],
	        		           	       	     														metric_data_processed["scc-about-tumblrshare-30"],metric_data_processed["scc-about-tumblrshare-7"],metric_data_processed["scc-about-tumblrshare-1"]], 
	        		           	       	     	        				 {		
	        		           	       	     	           			title:'sc-about-tumblrshare (category) -  "Share to Tumblr" impressions vs conversions (from the about tab)',
	        		           	       	     	           			axes: {
	        		           	       	     	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	        		           	       	     	               	        yaxis: {
	        		           	       	     	               	        	min: 0,
	        		           	       	     	               	        }
	        		           	       	     	           			},
	        		           	       	                    	        legend:{
	        		           	       	     	        				show:true,
	        		           	       	     	        				location:"nw",
	        		           	       	     	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
	        		           	       	     	        			},
	        		           	       	     	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
	        		           	       	     	           		});
	        		           	        		
	        		           	        		
	        		           	        		var sc_about_cws = $.jqplot('sc-about-cws', [metric_data_processed["sci-about-cws-30"],metric_data_processed["sci-about-cws-7"],metric_data_processed["sci-about-cws-1"],
	        		           	       	     														metric_data_processed["scc-about-cws-30"],metric_data_processed["scc-about-cws-7"],metric_data_processed["scc-about-cws-1"]], 
	        		           	       	     	        				 {		
	        		           	       	     	           			title:'sc-about-cws (category) - "Rate 5 stars in CWS" impressions vs conversions (from the about tab)',
	        		           	       	     	           			axes: {
	        		           	       	     	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	        		           	       	     	               	        yaxis: {
	        		           	       	     	               	        	min: 0,
	        		           	       	     	               	        }
	        		           	       	     	           			},
	        		           	       	                    	        legend:{
	        		           	       	     	        				show:true,
	        		           	       	     	        				location:"nw",
	        		           	       	     	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
	        		           	       	     	        			},
	        		           	       	     	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
	        		           	       	     	           		});
	        		           	        		
	        		           	        		var sc_footer_facebook_apppage = $.jqplot('sc_footer_facebook_apppage', [metric_data_processed["sci-footer-facebook_apppage-30"],metric_data_processed["sci-footer-facebook_apppage-7"],metric_data_processed["sci-footer-facebook_apppage-1"],
	        		           			        		           														metric_data_processed["scc-footer-facebook_apppage-30"],metric_data_processed["scc-footer-facebook_apppage-7"],metric_data_processed["scc-footer-facebook_apppage-1"]], 
	        		           			        		           	        				 {		
	        		           			        		           	           			title:'sc-footer-facebook_apppage (category) - "Like us on Facebook" impressions vs conversions (from the footer)',
	        		           			        		           	           			axes: {
	        		           			        		           	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
	        		           			        		           	               	        yaxis: {
	        		           			        		           	               	        	min: 0,
	        		           			        		           	               	        }
	        		           			        		           	           			},
	        		           			        		                          	        legend:{
	        		           			        		           	        				show:true,
	        		           			        		           	        				location:"nw",
	        		           			        		           	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
	        		           			        		           	        			},
	        		           			        		           	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
	        		           			        		           	           		});
	        		           	        		
	        		           	        		var sc_footer_twitter_mainacct = $.jqplot('sc_footer_twitter_mainacct', [metric_data_processed["sci-footer-twitter_mainacct-30"],metric_data_processed["sci-footer-twitter_mainacct-7"],metric_data_processed["sci-footer-twitter_mainacct-1"],
		        		           			        		           														metric_data_processed["scc-footer-twitter_mainacct-30"],metric_data_processed["scc-footer-twitter_mainacct-7"],metric_data_processed["scc-footer-twitter_mainacct-1"]], 
		        		           			        		           	        				 {		
		        		           			        		           	           			title:'sc-footer-twitter_mainacct (category) - "Follow @words4chrome on Twitter" impressions vs conversions (from the footer portion of the overlay)',
		        		           			        		           	           			axes: {
		        		           			        		           	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
		        		           			        		           	               	        yaxis: {
		        		           			        		           	               	        	min: 0,
		        		           			        		           	               	        }
		        		           			        		           	           			},
		        		           			        		                          	        legend:{
		        		           			        		           	        				show:true,
		        		           			        		           	        				location:"nw",
		        		           			        		           	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
		        		           			        		           	        			},
		        		           			        		           	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
		        		           			        		           	           		});
	        		           	        		
	        		           	        		
	        		           	        		var sc_about_twitter_mainacct = $.jqplot('sc_about_twitter_mainacct', [metric_data_processed["sci-about-twitter_mainacct-30"],metric_data_processed["sci-about-twitter_mainacct-7"],metric_data_processed["sci-about-twitter_mainacct-1"],
		        		           			        		           														metric_data_processed["scc-about-twitter_mainacct-30"],metric_data_processed["scc-about-twitter_mainacct-7"],metric_data_processed["scc-about-twitter_mainacct-1"]], 
		        		           			        		           	        				 {		
		        		           			        		           	           			title:'sc-about-twitter_mainacct (category) - "Follow @words4chrome on Twitter" impressions vs conversions (from the about tab)',
		        		           			        		           	           			axes: {
		        		           			        		           	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
		        		           			        		           	               	        yaxis: {
		        		           			        		           	               	        	min: 0,
		        		           			        		           	               	        }
		        		           			        		           	           			},
		        		           			        		                          	        legend:{
		        		           			        		           	        				show:true,
		        		           			        		           	        				location:"nw",
		        		           			        		           	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
		        		           			        		           	        			},
		        		           			        		           	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
		        		           			        		           	           		});
	        		           	        		
	        		           	        		var sc_about_twitter_persacct = $.jqplot('sc_about_twitter_persacct', [metric_data_processed["sci-about-twitter_persacct-30"],metric_data_processed["sci-about-twitter_persacct-7"],metric_data_processed["sci-about-twitter_persacct-1"],
		        		           			        		           														metric_data_processed["scc-about-twitter_persacct-30"],metric_data_processed["scc-about-twitter_persacct-7"],metric_data_processed["scc-about-twitter_persacct-1"]], 
		        		           			        		           	        				 {		
		        		           			        		           	           			title:'sc-about-twitter_persacct (category) - "Follow @fivedogit on Twitter" impressions vs conversions (from the about tab) (corrupted, fixed 7/30/14)',
		        		           			        		           	           			axes: {
		        		           			        		           	           				xaxis:{renderer:$.jqplot.DateAxisRenderer},
		        		           			        		           	               	        yaxis: {
		        		           			        		           	               	        	min: 0,
		        		           			        		           	               	        }
		        		           			        		           	           			},
		        		           			        		                          	        legend:{
		        		           			        		           	        				show:true,
		        		           			        		           	        				location:"nw",
		        		           			        		           	        				labels: ["impressions30","impressions7","impressions1","conversions30","conversions7","conversions1"]
		        		           			        		           	        			},
		        		           			        		           	        			seriesColors: [ "midnightblue", "mediumblue", "cornflowerblue", "darkred", "red", "pink"],
		        		           			        		           	           		});
	        		           	        		
	        		           	        	
	        		           	        		
	        	}
	        }
	        ,
	        error: function (XMLHttpRequest, textStatus, errorThrown) {
	        	alert("getMetricData ajax error");
	            console.log(textStatus, errorThrown);
	        }
		});
	//}
});

