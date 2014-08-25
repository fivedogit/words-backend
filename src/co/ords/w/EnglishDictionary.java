
package co.ords.w;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Random;

public class EnglishDictionary {

	/**
	 * @param args
	 */
	private File dictionaryfile;
	private Random randomGenerator;
	private ArrayList<String> catalogue;
	
	public EnglishDictionary()
	{
		catalogue = new ArrayList<String>();
		randomGenerator = new Random();
		dictionaryfile = new File("/home/cyrus/Desktop/projects/words_catchall/US.dic");
		BufferedReader reader;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(dictionaryfile), "UTF-8"));
			try {
				  while (true) {
				    String line;
				    line = reader.readLine();
				    if (line == null) break;
				    catalogue.add(line);
				    // process fields here
				  }
				} finally {
				  reader.close();
				}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public String getRandomWord(int minsize, int maxsize)
    {
		if(minsize == 0)
			return "";
		String word = "";
		int index = 0;
		while(word.length() < minsize || word.length() > maxsize)
		{
			index = randomGenerator.nextInt(catalogue.size());
			word = catalogue.get(index);
		}
        return word;
    }
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		EnglishDictionary ed = new EnglishDictionary();
		int x = 0;
		while(x < 100)
		{
			//System.out.println(ed.getRandomWord(3, 10));
			x++;
		}
	}

}
