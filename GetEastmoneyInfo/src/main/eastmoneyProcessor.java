package main;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Html;

/**
 * 对东方财富网的股吧评论（标题title）进行爬取http://guba.eastmoney.com/<br>
 * 难点：1、日期只有月日，没有年份<br>
 * 难点：2、随着page数的增加，日期按着逆序进行，但是其中不时会掺杂错序时间<br>
 * @author Jason Ye
 * @version 1.0
 * @date 2017.2.9
 *
 */
public class eastmoneyProcessor implements PageProcessor 
{
	String stockID="";
	String stopDate="";

	public eastmoneyProcessor(String stockID,String stopDate)
	{
		this.stockID=stockID;
		this.stopDate=stopDate;
	}

	private Site site = Site.me().setCycleRetryTimes(10).setSleepTime(1000).setCharset("utf-8");

	@SuppressWarnings({ "deprecation", "resource" })
	@Override
	public void process(Page page) 
	{
		Connection con = null;
		Statement stmt = null;
		ResultSet rs = null;
		try {
			Class.forName("com.mysql.jdbc.Driver") ;
			String url = "jdbc:mysql://localhost:3306/stocktest?useSSL=false&characterEncoding=UTF-8" ;   
			String username = "root" ;   
			String password = "lanhua" ;   
			con = DriverManager.getConnection(url , username , password );
			stmt = con.createStatement();
		} catch (ClassNotFoundException | SQLException e1) {
			e1.printStackTrace();
		}   

		//增量爬取or常规回溯爬取   根据datetemp表的情况进行选择
		ArrayList<Integer> flagCategory=new ArrayList<>();
		try {
			rs = stmt.executeQuery("SELECT DISTINCT Flag FROM datetemp WHERE StockID = '"+stockID+"';") ;
			while(rs.next())
			{
				flagCategory.add(rs.getInt("Flag"));
			}
		} catch (SQLException e1) {
			e1.printStackTrace();
		}

		int stopFlag=1;//当到达指定日期，则停止爬取。1：继续爬取        0：stop
		//增量爬取      flag的种类只有1时，只爬取from今天日期to上次爬取后离今天最近的日期
		if(flagCategory.size()==1 && flagCategory.get(0).intValue()==1)
		{
			Date recentDate = null;
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			//取出数据库中离今天最近的日期 recentDate
			try {
				rs = stmt.executeQuery("SELECT PastDate FROM datetemp2 WHERE StockID = '"+stockID+"';") ;
				while(rs.next())
				{
					recentDate=rs.getDate("PastDate");
					recentDate=df.parse(recentDate.toString());
				}
			} catch (SQLException | ParseException e1) {
				e1.printStackTrace();
			}
			//从网页中抽取 read review title publish_date 四个指标
			List<String> articleh = page.getHtml().xpath("//div[@id='articlelistnew']//div[@class='articleh']").all();
			String readVolume;
			String reviewVolume;
			String title;
			String publish_date;
			int count=0;
			int count2=0;
			Date publishDate=null;
			for(int i=0; i<articleh.size();i++)
			{
				count2=i;
				String content=articleh.get(i);
				String check=Html.create(content).xpath("//div[@class='articleh']//span[@class='l3']/em/text()").toString();  //提取链接
				if(check!=null)
					continue;
				else
				{
					stockSentimentBean ssb=new stockSentimentBean();
					readVolume=Html.create(content).xpath("//div[@class='articleh']//span[@class='l1']/text()").toString();  //提取链接
					reviewVolume=Html.create(content).xpath("//div[@class='articleh']//span[@class='l2']/text()").toString();  //提取链接
					title=Html.create(content).xpath("//div[@class='articleh']//span[@class='l3']/a/@title").toString();  //提取链接
					publish_date=Html.create(content).xpath("//div[@class='articleh']//span[@class='l6']/text()").toString();  //提取链接

					//为publishDate选择年份
					try {
						publishDate = OperatePublishDate(publish_date,stmt);
					} catch (ParseException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}

					if(publishDate.getTime() > recentDate.getTime())
					{
						//将爬取的数据传给MySQLPipeline
						ssb.setStockID(stockID);
						ssb.setReadVolume(readVolume);
						ssb.setReviewVolume(reviewVolume);
						ssb.setTitle(title);
						int year=publishDate.getYear()+1900;
						int month=publishDate.getMonth()+1;
						int day=publishDate.getDate();
						String stringDate=year+"-"+month+"-"+day;
						ssb.setPublish_date(stringDate);
						page.putField(""+count,ssb);
						count++;
					}else//将比recentDate后一天（明天）的日期的flag=0置为flag=1
						//将datetemp2表中的日期更新为新的最近日期
					{
						String stringDate = null;
						//将比recentDate后一天（明天）的日期的flag=0置为flag=1
						try {
							rs = stmt.executeQuery("SELECT PastDate FROM datetemp WHERE Flag=0 AND StockID = '"+stockID+"';") ;
							while(rs.next())
							{
								stringDate=rs.getDate("PastDate").toString();
							}
							String updateString="UPDATE DateTemp SET Flag=1 WHERE StockID = '"+stockID+"' AND PastDate = '"+stringDate+"';";
							stmt.executeUpdate(updateString);
						} catch (SQLException e) {
							e.printStackTrace();
						}
						//将datetemp2表中的日期跟新为新的最近日期
						String stringRecentDate = null;
						try {
							rs = stmt.executeQuery("SELECT PastDate FROM datetemp WHERE StockID = '"+stockID+"' ORDER BY PastDate DESC LIMIT 1;") ;
							while(rs.next())
							{
								stringRecentDate=rs.getDate("PastDate").toString();
							}

							String updateDateTemp2String="UPDATE DateTemp2 SET PastDate='"+stringRecentDate+"' WHERE StockID = '"+stockID+"';";
							stmt.executeUpdate(updateDateTemp2String) ;
						} catch (SQLException e) {
							e.printStackTrace();
						}
						break;
					}
				}
			}
			if(count2==articleh.size()-1 && publishDate.getTime() > recentDate.getTime())//如果本页爬取完了  日期还是小
			{
				//递增f_的代码块,如果到了最后一页就不增加了
				String string=page.getUrl().toString();
				String left=string.substring(0, 40);
				String right=string.substring(string.length()-5);
				String center=string.substring(40);
				String[] numString=new String[2];
				numString=center.split("\\.");
				int currentPageNum=Integer.parseInt(numString[0]);//当前page的f_数目
				currentPageNum++;
				String num=currentPageNum+"";
				page.addTargetRequest(left+num+right);
			}
		}
		//常规回溯爬取       flag的种类有1、0     或者 flag的种类为0      或者flag为null时进行常规回溯爬取
		else
		{
			//得到总的评论数，并计算总页数pageNUm
			String totalNumString=page.getHtml().xpath("//div[@id='articlelistnew']//span[@class='pagernums']/@data-pager").toString();
			totalNumString=totalNumString.substring(15);
			String[] AAA=totalNumString.split("\\|");
			int totalNum=Integer.parseInt(AAA[0]);
			int pageNUm=totalNum/80+1;

			//获取当前网页数currentPageNum
			String string=page.getUrl().toString();
			String left=string.substring(0, 40);
			String right=string.substring(string.length()-5);
			String center=string.substring(40);
			String[] numString=new String[2];
			numString=center.split("\\.");
			int currentPageNum=Integer.parseInt(numString[0]);//当前page的f_数目 

			//抽取 read review title publish_date 四个数据
			List<String> articleh = page.getHtml().xpath("//div[@id='articlelistnew']//div[@class='articleh']").all();
			String readVolume;
			String reviewVolume;
			String title;
			String publish_date;
			int count=0;
			int count2=0;
			String stringDate = null;
			for(int i=0; i<articleh.size();i++)
			{
				count2=i;
				String content=articleh.get(i);
				String check=Html.create(content).xpath("//div[@class='articleh']//span[@class='l3']/em/text()").toString();  //提取链接
				if(check!=null)
					continue;
				else
				{
					stockSentimentBean ssb=new stockSentimentBean();
					readVolume=Html.create(content).xpath("//div[@class='articleh']//span[@class='l1']/text()").toString();  //提取链接
					reviewVolume=Html.create(content).xpath("//div[@class='articleh']//span[@class='l2']/text()").toString();  //提取链接
					title=Html.create(content).xpath("//div[@class='articleh']//span[@class='l3']/a/@title").toString();  //提取链接
					publish_date=Html.create(content).xpath("//div[@class='articleh']//span[@class='l6']/text()").toString();  //提取链接

					Date publishDate=null;
					try {
						publishDate=OperatePublishDate(publish_date,stmt);//确定日期的具体年份
						int year=publishDate.getYear()+1900;
						int month=publishDate.getMonth()+1;
						int day=publishDate.getDate();
						stringDate=year+"-"+month+"-"+day;
						SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
						Date stringDate2=df.parse(stringDate);
						Date stopDate2=df.parse(stopDate);
						if(stringDate2.getTime()>=stopDate2.getTime())//到达指定日期，停止爬取
						{
							stopFlag=0;
							break;
						}
					} catch (ParseException e) {
						e.printStackTrace();
					}
					//将爬取的东西给MySQLPipeline
					ssb.setStockID(stockID);
					ssb.setReadVolume(readVolume);
					ssb.setReviewVolume(reviewVolume);
					ssb.setTitle(title);
					ssb.setPublish_date(stringDate);
					page.putField(""+count,ssb);
					count++;
				}
			}

			//最后一页的最后一条数据的日期 或者  到达指定的日期
			//将该日期在datetemp表中相应的flag字段置为1，因为后续没有新日期更迭    
			//将离今天最近的日期存入表datetemp2
			if((currentPageNum==pageNUm && count2==articleh.size()-1) || stopFlag==0)
			{
				String updateString="UPDATE DateTemp SET Flag=1 WHERE StockID = '"+stockID+"' AND PastDate = '"+stringDate+"';";
				try {
					stmt.executeUpdate(updateString) ;
				} catch (SQLException e) {
					e.printStackTrace();
				}

				Date recentDate = null;
				try {
					rs = stmt.executeQuery("SELECT PastDate FROM datetemp WHERE StockID = '"+stockID+"' ORDER BY PastDate DESC LIMIT 1;") ;
					while(rs.next())
					{
						recentDate=rs.getDate("PastDate");
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}

				String update2String="INSERT INTO DateTemp2(PastDate,StockID) VALUES ('"+recentDate+"','"+stockID+"');";
				try {
					stmt.executeUpdate(update2String) ;
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}

			//将新的url给爬虫
			if(currentPageNum<pageNUm && stopFlag==1)
			{
				currentPageNum++;
				String num=currentPageNum+"";
				page.addTargetRequest(left+num+right);
			}
		}
		if(rs!= null)
			try {
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		if(stmt!= null)
			try {
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		if(con!= null)
			try {
				con.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
	}

	@Override
	public Site getSite() {
		return site;
	}

	/**
	 * 通过对datetemp表和datetemp2表的操作，确定publish_date的年份以及为了后续日期回溯爬取进行操作
	 * 
	 * @param publish_date
	 * @param stmt
	 * @return
	 * @throws ParseException
	 */
	@SuppressWarnings("deprecation")
	private Date OperatePublishDate(String publish_date,Statement stmt) throws ParseException 
	{
		//获取todayYear
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		String todaySysDate=df.format(new Date());
		String todayYear=todaySysDate.substring(0, 4);

		//将传入的publish_date转化为Date型 年份置为系统年份的 publishDate
		publish_date=todayYear+"-"+publish_date;
		Date publishDate = df.parse(publish_date);
		Date publishDateOld = df.parse("1000-1-1");
		final Date finalDate = df.parse("1000-1-1");

		ResultSet rs = null;
		try {
			rs = stmt.executeQuery("SELECT PastDate FROM datetemp WHERE Flag = 0 AND StockID = '"+stockID+"';") ;
			while(rs.next())
			{
				publishDateOld=rs.getDate("PastDate");
				publishDateOld=df.parse(publishDateOld.toString());
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}finally {
			if(rs!= null)
				try {
					rs.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
		}

		//以下的if else 是为了确定publishDate的年份
		//数据库中无数据  从无到有   第一次  初始化
		if(publishDateOld.getTime()==finalDate.getTime())
		{
			int year=publishDate.getYear()+1900;
			int month=publishDate.getMonth()+1;
			int day=publishDate.getDate();
			String insertString="INSERT INTO DateTemp(StockID,PastDate,Flag) VALUES ('"
					+stockID+"','"+year+"-"+month+"-"+day+"',0);";
			try {
				stmt.executeUpdate(insertString) ;
			} catch (SQLException e) {
				e.printStackTrace();
			}
			publishDateOld=publishDate;
		}
		//数据库有记录
		else
		{
			publishDate=IdentifyPublishDateYear(publishDate,stmt);//该函数只涉及查询 不会对数值操作 只查询flag 0 1 null 情况
			publishDate=DeleteExceptionDate(publishDate,publishDateOld,stmt);
			NewAndOldDateExchange(publishDate,publishDateOld,stmt);
			FillInRestDate(publishDate,publishDateOld,stmt);
		}
		return publishDate;
	}

	/**
	 * OperatePublishDateYear函数的子函数<br>
	 * 确定publish_date的年份
	 * @param PublishDate
	 * @param stmt
	 * @return
	 */
	@SuppressWarnings("deprecation")
	private Date IdentifyPublishDateYear(Date PublishDate,Statement stmt)  
	{
		//获取系统时间
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		String todaySysDate=df.format(new Date());
		Date SystemDate = null;
		try {
			SystemDate = df.parse(todaySysDate);
		} catch (ParseException e) {
			e.printStackTrace();
		}

		boolean flag=true;
		Date publishDate=PublishDate;
		if(publishDate.getTime() > SystemDate.getTime())
			publishDate.setYear(publishDate.getYear()-1);

		while(flag)
		{
			int count=0;
			int Flag = 100;

			ResultSet rs = null;
			try {
				int year=publishDate.getYear()+1900;
				int month=publishDate.getMonth()+1;
				int day=publishDate.getDate();
				rs = stmt.executeQuery("SELECT Flag FROM datetemp WHERE StockID = '"+stockID+"' AND PastDate = '"+year+"-"+month+"-"+day+"';") ;
				while(rs.next())
				{
					Flag=rs.getInt("Flag");
					count++;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}finally {
				if(rs!= null)
					try {
						rs.close();
					} catch (SQLException e) {
						e.printStackTrace();
					}
			}

			if(count>1)
				System.out.println("IdentifyPublishDateYear!!查询到相同日期下多个flag值");

			if(Flag==0 || Flag==100)
			{
				flag=false;
				break;
			}else if(Flag==1)
			{
				publishDate.setYear(publishDate.getYear()-1);
			}

		}
		return publishDate;
	}

	/**
	 * OperatePublishDateYear函数的子函数<br>
	 * 将乱入的日期处理，使得可以继续回溯爬取<br>
	 * 目前函数处理乱入了小日期这种情况（2017.1.14   2017.1.3    2017.1.14）<br>
	 * （目前尚未发现乱入了大日期这种情况2017.1.14   2017.1.16    2017.1.14）
	 * @param publishDate
	 * @param publishDateOld
	 * @param stmt
	 * @return
	 */
	@SuppressWarnings("deprecation")
	private Date DeleteExceptionDate(Date publishDate,Date publishDateOld,Statement stmt) 
	{
		ArrayList<String> dateMonthDayList=new ArrayList<>(5);
		String dateYearMonthDay[]=new String[5];
		ResultSet rs = null;
		//从DateTemp中取出5个距离今天最远日期的月日  与当前publishDate的月日比较， 若一致 说明有乱入
		try {
			rs = stmt.executeQuery("SELECT PastDate FROM DateTemp WHERE Flag=1 ORDER BY PastDate LIMIT 5;") ;
			int count=0;
			while(rs.next())
			{
				dateYearMonthDay[count]=rs.getDate("PastDate").toString();
				dateMonthDayList.add(rs.getDate("PastDate").toString().substring(5));//只取出月日
				count++;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}finally {
			if(rs!= null)
				try {
					rs.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
		}

		int month=publishDate.getMonth()+1;
		int day=publishDate.getDate();
		String publishDateMonthDay=month+"-"+day;

		SimpleDateFormat df = new SimpleDateFormat("MM-dd");
		Date uDatepublishDateMonthDay = null;
		try {
			uDatepublishDateMonthDay = df.parse(publishDateMonthDay);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		if(dateMonthDayList.size()==0)
			return publishDate;

		Date uDatedateMonthDay0 = null;
		try {
			uDatedateMonthDay0 = df.parse(dateMonthDayList.get(0));
		} catch (ParseException e) {
			e.printStackTrace();
		}
		/*Date uDatedateMonthDay1 = df.parse(dateMonthDayList.get(1));
		Date uDatedateMonthDay2 = df.parse(dateMonthDayList.get(2));
		Date uDatedateMonthDay3 = df.parse(dateMonthDayList.get(3));
		Date uDatedateMonthDay4 = df.parse(dateMonthDayList.get(4));*/

		int monthpublishDateOld=publishDateOld.getMonth()+1;
		int daypublishDateOld=publishDateOld.getDate();
		String aaa=monthpublishDateOld+"-"+daypublishDateOld;
		Date uDatedateMonthDayOld = null;
		try {
			uDatedateMonthDayOld = df.parse(aaa);
		} catch (ParseException e) {
			e.printStackTrace();
		}

		if(uDatepublishDateMonthDay.getTime()>uDatedateMonthDayOld.getTime() 
				&& uDatepublishDateMonthDay.getTime() <= uDatedateMonthDay0.getTime())//夹杂了小日期（2017.1.14   2017.1.3    2017.1.14）
		{
			System.out.println("中间掺杂了小日期！！！");
			String deleteString="DELETE FROM DateTemp WHERE Flag=0 AND StockID = '"+stockID+"' ;";
			try {
				stmt.executeUpdate(deleteString) ;
			} catch (SQLException e) {
				e.printStackTrace();
			}

			String updateString="UPDATE DateTemp SET Flag=0 WHERE StockID = '"+stockID+"' AND PastDate = '"+dateYearMonthDay[0]+"';";
			try {
				stmt.executeUpdate(updateString) ;
			} catch (SQLException e) {
				e.printStackTrace();
			}

			publishDate.setYear(publishDateOld.getYear());
			return publishDate;
		}/*else if()//夹杂了大日期（2017.1.14   2017.1.16    2017.1.14）
				{
			     System.out.println("中间掺杂了大日期！！！");
			     publishDate.setYear(publishDateOld.getYear()+1);
			     return publishDate;
				}*/
		return publishDate;
	}


	/**
	 * 新旧日期更迭，当新日期出现时，插入datetemp表中新日期（flag=0），旧日期的相应flag字段置1
	 * @param publishDate
	 * @param publishDateOld
	 * @param stmt
	 */
	@SuppressWarnings("deprecation")
	private void NewAndOldDateExchange(Date publishDate,Date publishDateOld,Statement stmt)
	{
		//新旧日期更迭，当新日期出现flag=0，旧日期flag=1
		if(publishDate.getTime() < publishDateOld.getTime())
		{
			int year=publishDateOld.getYear()+1900;
			int month=publishDateOld.getMonth()+1;
			int day=publishDateOld.getDate();

			int year2=publishDate.getYear()+1900;
			int month2=publishDate.getMonth()+1;
			int day2=publishDate.getDate();

			String insertString="INSERT INTO DateTemp(StockID,PastDate,Flag) VALUES ('"+stockID+"','"+year2+"-"+month2+"-"+day2+"',0);";
			int i=0;
			try {
				i=stmt.executeUpdate(insertString) ;
			} catch (SQLException e) {
				e.printStackTrace();
			}
			if(i!=0)
			{
				String updateString="UPDATE DateTemp SET Flag=1 WHERE StockID = '"+stockID+"' AND PastDate = '"+year+"-"+month+"-"+day+"';";
				try {
					stmt.executeUpdate(updateString) ;
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}

	}

	/**
	 * 如果新旧日期的年份不同，说明到了新的一年，过去的一年有些日期没有人发评论，所以要将日期填满，以防下一年年份变成今年的年份
	 * @param publishDate
	 * @param publishDateOld
	 * @param stmt
	 */
	@SuppressWarnings("deprecation")
	private void FillInRestDate(Date publishDate,Date publishDateOld,Statement stmt)  
	{
		ResultSet rs = null;
		int publishDateFlag=3;
		int publishDateOldFlag =4;

		int year=publishDate.getYear()+1900;
		int month=publishDate.getMonth()+1;
		int day=publishDate.getDate();
		int year2=publishDateOld.getYear()+1900;
		int month2=publishDateOld.getMonth()+1;
		int day2=publishDateOld.getDate();
		
		try {
			rs = stmt.executeQuery("SELECT Flag FROM datetemp WHERE PastDate = '"+year+"-"+month+"-"+day+"' AND StockID = '"+stockID+"';") ;
			while(rs.next())
			{
				publishDateFlag=rs.getInt("Flag");
			}
			rs = stmt.executeQuery("SELECT Flag FROM datetemp WHERE PastDate = '"+year2+"-"+month2+"-"+day2+"' AND StockID = '"+stockID+"';") ;
			while(rs.next())
			{
				publishDateOldFlag=rs.getInt("Flag");
			}
		} catch (SQLException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}

		//如果新旧日期的年份不同，说明到了新的一年，过去的一年有些日期没有人发评论，所以要将日期填满，以防下一年年份变成今年的年份
		//还要满足新旧日期在datetemp表中的Flag都是1，以防这种情况2017-1-3  2016-12-28  2017-1-1（2016-1-1），使得DeleteExceptionDate与该函数冲突
		if(publishDate.getYear() < publishDateOld.getYear() && publishDateOldFlag==publishDateFlag )
		{
			int oldYear=publishDateOld.getYear()+1900;

			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			String todaySysDate=df.format(new Date());
			Date SystemDate = null;
			try {
				SystemDate = df.parse(todaySysDate);
			} catch (ParseException e1) {
				e1.printStackTrace();
			}
			int SystemYear=SystemDate.getYear()+1900;
			int SystemMonth=SystemDate.getMonth()+1;
			int SystemDay=SystemDate.getDate();

			Calendar start = Calendar.getInstance();
			Calendar end = Calendar.getInstance();
			if(oldYear==SystemYear)
			{
				start.set(SystemYear, 0,1); 
				end.set(SystemYear, SystemMonth-1,SystemDay);
			}else
			{
				start.set(oldYear, 0, 1);
				end.set(oldYear, 11,31);
			}
			Long startTIme = start.getTimeInMillis();  
			Long endTime = end.getTimeInMillis();  
			Long oneDay = 1000 * 60 * 60 * 24l;  
			Long time = startTIme; 


			while (time <= endTime) 
			{  
				Date d = new Date(time); 
				String everyDay=df.format(d);
				int Flag=100;
				try {
					String select="SELECT Flag FROM datetemp WHERE PastDate = '"+everyDay+"' AND StockID = '"+stockID+"';";
					rs = stmt.executeQuery(select) ;
					while(rs.next())
					{
						Flag=rs.getInt("Flag");
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}

				if(Flag==100)
				{
					String insertString="INSERT INTO DateTemp(StockID,PastDate,Flag) VALUES ('"
							+stockID+"','"+everyDay+"',1);";
					try {
						stmt.executeUpdate(insertString) ;
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}

				time += oneDay;  
			} 
		}
		if(rs!= null)
			try {
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
	}

	public static void main(String[] args) throws ClassNotFoundException, SQLException {

		String stockID="002230";
		Spider.create(new eastmoneyProcessor(stockID,"2017-1-14"))//input stockID
		.addUrl("http://guba.eastmoney.com/list,"+stockID+",f_1.html")//input stockID page f_1.html
		.addPipeline(new MySQLPipeline())
		.thread(1)
		.run();
	}

}

