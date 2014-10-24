var devel = true; 
var endpoint = "https://w.ords.co/endpoint";
if(devel === true)
	endpoint = "http://localhost:8080/words/endpoint";

function getParameterByName(name) {
     name = name.replace(/[\[]/, "\\\[")
         .replace(/[\]]/, "\\\]");
     var regexS = "[\\?&]" + name + "=([^&#]*)";
     var regex = new RegExp(regexS);
     var results = regex.exec(window.location.search);
     if (typeof results === "undefined") return "";
     else return decodeURIComponent(results[1].replace(/\+/g, " "));
 }
var id = getParameterByName("id");
	
$.ajax({
	type: 'GET',
	url: endpoint,
	data: {
		method: "noteConversion",
		id: id
	},
	dataType: 'json',
	async: true,
	success: function (data, status) {
		// doesn't matter if it succeeded or failed. We have to try to send the user to their destination
		if(data.target === null ||  data.target === "")
			$("#main_div").text("Encountered an error. Cannot complete the request. Very sorry.");
		else if(data.target.indexOf("facebook") != -1)
			window.location = "https://www.facebook.com/sharer/sharer.php?u=http%3A%2F%2Fwww.words4chrome.com";
		else if(data.target.indexOf("twitter") != -1)
			window.location = "https://twitter.com/intent/tweet?text=WORDS%20for%20Chrome%3A%20Web%20comments%20for%20smart%20people&url=http%3A%2F%2Fwww.words4chrome.com";
		else if(data.target.indexOf("googleplus") != -1)
			window.location = "https://plus.google.com/share?url=http%3A%2F%2Fwww.words4chrome.com";
		else if(data.target.indexOf("tumblr") != -1)
			window.location = "http://www.tumblr.com/share?v=3&u=http%3A%2F%2Fwww.words4chrome.com&t=WORDS%20for%20Chrome%3A%20Web%20comments%20for%20smart%20people";
		else if(data.target.indexOf("gmail") != -1)
			window.location = "https://mail.google.com/mail/?view=cm&fs=1&su=WORDS%20for%20Chrome&body=Hey%2C%20I%20thought%20you%20might%20like%20this.%20It%27s%20a%20new%20kind%20of%20web%20commenting%20system%20that%20protects%20privacy%20and%20keeps%20out%20the%20crazies.%20%0A%0Ahttp%3A%2F%2Fwww.words4chrome.com%0A%0AYou%20can%20download%20Chrome%20if%20you%20don%27t%20already%20have%20it.%0A%0AEnjoy!";
		else
		{
			$("#main_div").text("Unknown target. Cannot complete the request. Very sorry.");
		}	
	},
	error: function (XMLHttpRequest, textStatus, errorThrown) {
		console.log(textStatus, errorThrown);
	} 
});  
