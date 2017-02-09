package main;

import java.sql.*;

public class JDBCMySQL 
{

	public int DoInsert(String sql) 
	{
		int rows =0;
		Connection con = null;
		Statement stmt = null;
		try {
			//加载MySql的驱动类   
			Class.forName("com.mysql.jdbc.Driver") ;   
			//连接MySql数据库，用户名和密码都是root   
			String url = "jdbc:mysql://localhost:3306/stocktest?useSSL=false&characterEncoding=UTF-8" ;   
			String username = "root" ;   
			String password = "lanhua" ;   
			con= DriverManager.getConnection(url , username , password );
			stmt = con.createStatement() ; 
			rows=stmt.executeUpdate(sql) ;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally 
		{
			if(stmt!= null)
				try {
					stmt.close();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
					
			if(con!= null)
				try {
					con.close();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		return rows;
	}
}
