package main;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.huaban.analysis.jieba.JiebaSegmenter.SegMode;

import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;

public class MySQLPipeline implements Pipeline {

	@Override
	public void process(ResultItems resultItems, Task task) {
		
		
		for (Map.Entry<String, Object> entry : resultItems.getAll().entrySet()) {
            //System.out.println(entry.getKey() + ":\t" + entry.getValue());
            
            stockSentimentBean ssb=(stockSentimentBean) entry.getValue();
            
            String stockID=ssb.getStockID();
            String readVolume=ssb.getReadVolume();
            String title=ssb.getTitle();
            String publish_date=ssb.getPublish_date(); 
            String reviewVolume=ssb.getReviewVolume();
            
            //结巴分词
            JiebaSegmenter segmenter = new JiebaSegmenter();
            List<SegToken> listFirst = segmenter.process(title, SegMode.SEARCH);
            String line_sentence_first=null;
            if(listFirst.size()>0)
		     {
		    	 line_sentence_first=listFirst.get(0).word;
			     for(int i=1;i<listFirst.size();i++)
			    	{
			    	 line_sentence_first=line_sentence_first+" "+listFirst.get(i).word;
			    	}
		     }
            
            //去除非中文字
            cleanWords cWords=new cleanWords();
            String cleantitle=cWords.onlyChinese(line_sentence_first);
            
            //把爬取内容扔到数据库
            String sqlString="INSERT INTO StockReviews(stockID,readVolume,title,publish_date,reviewVolume) VALUES "
            		+"('"+stockID+"','"+readVolume+"','"+cleantitle+"','"+publish_date+"','"+reviewVolume+"');";
            JDBCMySQL jjj=new JDBCMySQL();
			jjj.DoInsert(sqlString);
        }
	}

}
