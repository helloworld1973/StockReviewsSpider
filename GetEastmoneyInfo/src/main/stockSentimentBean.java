package main;

public class stockSentimentBean {
	
	
	public String getStockID() {
		return stockID;
	}
	public void setStockID(String stockID) {
		this.stockID = stockID;
	}
	public String getReadVolume() {
		return readVolume;
	}
	public void setReadVolume(String readVolume) {
		this.readVolume = readVolume;
	}
	public String getReviewVolume() {
		return reviewVolume;
	}
	public void setReviewVolume(String reviewVolume) {
		this.reviewVolume = reviewVolume;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getPublish_date() {
		return publish_date;
	}
	public void setPublish_date(String publish_date) {
		this.publish_date = publish_date;
	}
	private String stockID;
	private String readVolume;
	private String reviewVolume;
	private String title;
	private String publish_date;

}
