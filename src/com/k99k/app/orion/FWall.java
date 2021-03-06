/**
 * 
 */
package com.k99k.app.orion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.types.BasicBSONList;
import org.bson.types.ObjectId;
import org.stringtree.json.JSONReader;
import org.stringtree.json.JSONValidatingReader;

import com.k99k.otools.IO;
import com.k99k.otools.StringUnit;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.util.JSON;

/**
 * 处理图片请求
 * @author keel
 *
 */
public class FWall implements Runnable {
	
//	public static final String SORT_BY_TIME = "time";
//	public static final String SORT_BY_DOWN = "down";
//	public static final String SORT_BY_STAR = "star";
	
	public FWall(String iniFile){
		this.readIni(iniFile);
	}
	
//	/**
//	 * 配置文件位置
//	 */
//	private String iniFile;
	
	/**
	 * 索引Json文件位置
	 */
	private String indexJsonPath = "/WEB-INF/fw_index.json";
	
	private MongoCol mongoCol;//  = new MongoCol();
	
	private final JSONReader jsonReader = new JSONValidatingReader();
	
	/**
	 * 类别Map，用于缓存pic数据.查找路径:cateMap->排序字段(如"down")->序号(计算升降序得出序号)
	 * 
	 */
	private HashMap<String,ArrayList<String[]>> picCateMap = new HashMap<String,ArrayList<String[]>>(100);
	
	/**
	 * 类别总计缓存,避免size()操作
	 */
	private HashMap<String,Integer> cateCountMap = new HashMap<String, Integer>(100);
	
	/**
	 * ObjectID图片缓存,key为ObjectId,value为WallPic
	 */
	private HashMap<String,WallPic> objIdMap = new HashMap<String, WallPic>(5000);
	
	/**
	 * 用于排序的 picList
	 */
	private ArrayList<WallPic> picList;
	
	/**
	 * 用于排序的cateList
	 */
	private ArrayList<WallCate> cateList;
	/**
	 * 登录所获取的配置文件,为空String时重定向到fw_index.htm文件
	 */
	private String wallconfig = "";
	
	/**
	 * 准备生成的config
	 */
	private String preConfig = "";
	
	/**
	 * 非中国服务器的wallconfig
	 */
	private String wallconfig_us = "";
	
	/**
	 * 缓存文件路径
	 */
	private String cacheIniPath = "";
	private String cacheIniPath_us = "";
	
	
	/**
	 * 任务列表
	 */
	private final ArrayList taskList = new ArrayList(100);
	
	/**
	 * 运行状态
	 */
	private boolean isRun = true;
	
	/**
	 * 是否有新的加星操作，为true时将重建加星排序索引
	 */
	private boolean hasNewStar = false;
	
	/**
	 * 重建Star索引的间隔时间，单位为秒
	 */
	private int starSleep = 50;
	
	/**
	 * 暂停线程中的 任何处理
	 */
	private boolean pause = false;
	/**
	 * 下次重建Star的时间点
	 */
	private Date nextReIndexStarTime = new Date();
	
	/**
	 * 每天更新时间的小时数
	 */
	private int dayUpdateHour = 12;
	
	/**
	 * 每天更新时间的分钟数
	 */
	private int dayUpdateMin = 01;
	
	/**
	 * 北京时区 
	 */
	private final static TimeZone timeZone = TimeZone.getTimeZone("GMT+8");
	
	/**
	 * 广告比率
	 */
	private int k_woobooADcent = 50;
	private int s_woobooADcent = 50;
	
	
	/**
	 * @return the woobooADcent
	 */
	public final int getWoobooADcent(String ks) {
		if (ks.equals("k")) {
			return k_woobooADcent;
		}
		return s_woobooADcent;
	}


	/**
	 * @param woobooADcent the woobooADcent to set
	 */
	public final void setWoobooADcent(int woobooADcent,boolean isK) {
		if (isK) {
			this.k_woobooADcent = woobooADcent;
		}else{
			this.s_woobooADcent = woobooADcent;
		}
	}


	/**
	 * ### 适用于ver2.0版的请求 ###
	 * 由当前的url指向真正的图片地址
	 * @param url 原url
	 * @param sortType 排序顺序,0为顺序，非0为倒序
	 * @param sortBy 排序类型
	 * @return 出现异常时返回空String
	 */
	public final String[] getPicFromUrl(String url,String sortBy,int sortType){
		int picType = 0;
		String cate = "";
		int num = 0;
		try {
			String s = url.substring(url.lastIndexOf("/")+1,url.lastIndexOf("."));
			
			//是否大图
			if (s.startsWith("b_")) {
				//分辨率
				if (s.endsWith("_l")) {
					picType = 3;
				}else{
					picType = 2;
				}
			}else{
				//分辨率
				if (s.endsWith("_l")) {
					picType = 1;
				}else{
					picType = 0;
				}
			}
			
			Pattern pattern = Pattern.compile("([a-z]{2,})(\\d+)");
			Matcher matcher = pattern.matcher(s);
			if (matcher.find()) {
				cate = matcher.group(1);
				num = Integer.parseInt(matcher.group(2));
			}
			String[] to = this.toRealPath(cate, sortBy, num-1, picType, sortType);
			//出现异常时返回空String 
//			if (to.equals("")) {
//				return url;
//			}
			return to;
		} catch (Exception e) {
			System.out.println("------"+new Date());e.printStackTrace();
			return null;
		}
	}
	

	/**
	 * 由pic_oid获取真正的图片路径 
	 * @param pic_oid
	 * @return
	 */
	public final String[] getPicPathByOid(String pic_oid){
		int picType = 0;
		String oid = "";
		//是否大图
		if (pic_oid.startsWith("b_")) {
			//分辨率
			if (pic_oid.endsWith("_l")) {
				picType = 3;
				oid = pic_oid.substring(3,pic_oid.length()-2);
			}else{
				picType = 2;
				oid = pic_oid.substring(3);
			}
		}else{
			//分辨率
			if (pic_oid.endsWith("_l")) {
				picType = 1;
				oid = pic_oid.substring(1,pic_oid.length()-2);
			}else{
				picType = 0;
				oid = pic_oid.substring(1);
			}
		}
		WallPic w = objIdMap.get(oid);
		if (w != null) {
//			String[] arr =  this.picCateMap.get(w.getCate()+"#time").get(w.getPicId());
//			return arr[picType+1];
			String[] arr =  new String[]{oid,w.getPicPath()[picType+1]};
			return arr;
		}
		return null;
	}
	
	/**
	 * 转到真正的图片路径,如失败则返回空String
	 * @param cate 类别(catePre)
	 * @param sortBy 排序类别
	 * @param num 图片序号
	 * @param picType 图片所取对应图片
	 * @param isAsc 是否顺序,0为顺序，非0为倒序
	 * @return String[]
	 */
	public final String[] toRealPath(String cate,String sortBy,int num,int picType,int isAsc){
		int picNum = num;
		String[] re = new String[2];
		try {
			if (isAsc != 0) {
				//处理倒序
				int cateCount = this.cateCountMap.get(cate);
				picNum = cateCount - picNum - 1;
			}
			String[] arr =  this.picCateMap.get(cate+"#"+sortBy).get(picNum);
			re[0] = arr[0];
			re[1] = arr[picType+1];
			//String s = this.picCateMap.get(cate+"#"+sortBy).get(picNum)[picType+1];
			
			return re;
		} catch (Exception e) {
			//System.out.println("toRealPath error! "+cate+"#"+sortBy + " picNum:"+picNum+ " picType:"+picType);
			//System.out.println("------"+new Date());e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * 获取某用户的所有star图片列表
	 * @param imei 
	 * @return 数组形式的json String 
	 */
	public String getStarIndexByUser(String imei){
		if (!checkIMEI(imei)) {
			return "";
		}
		String starJson = "";
		DBCollection coll = mongoCol.getColl("wallUser");
		try {
			DBCursor cur = coll.find(new BasicDBObject("imei",imei));
			if (cur.hasNext()) {
				DBObject o = cur.next();
				if (o.containsField("star")) {
					BasicBSONList list = (BasicBSONList)o.get("star");
					StringBuilder sb = new StringBuilder();
					sb.append("[");
					int size = list.size();
					for (int i = 0; i < size; i++) {
						sb.append("\"").append(list.get(i)).append("\",");
					}
//					for (int i = 0; i < size; i++) {
//						WallPic w = objIdMap.get(list.get(i));
//						if (w == null) {
//							System.out.println("===========188 ERROR=======:"+list.get(i));
//							continue;
//						}
//						//FIXME 188行，报java.lang.NullPointerException
//						String catePicIdPath = w.getCate()+"#"+w.getPicId();
//						sb.append("\"").append(catePicIdPath).append("\",");
//					}
					if (sb.charAt(sb.length()-1) == ',') {
						sb.delete(sb.length()-1,sb.length());
					}
					sb.append("]");
					starJson = sb.toString();//((BasicBSONList)o.get("star")).toString();
				}
			}
		} catch (Exception e) {
			System.out.println("------"+new Date());
			e.printStackTrace();
			return "";
		}
		return starJson;
	}
	
	/**
	 * 获取某用户的所有star图片列表
	 * @param imei 
	 * @return 数组形式的json String 
	 */
	public String getStarIndexByUserOld(String imei){
		if (!checkIMEI(imei)) {
			return "";
		}
		String starJson = "";
		DBCollection coll = mongoCol.getColl("wallUser");
		try {
			DBCursor cur = coll.find(new BasicDBObject("imei",imei));
			if (cur.hasNext()) {
				DBObject o = cur.next();
				if (o.containsField("star")) {
					BasicBSONList list = (BasicBSONList)o.get("star");
					StringBuilder sb = new StringBuilder();
					sb.append("[");
					int size = list.size();
					for (int i = 0; i < size; i++) {
						WallPic w = objIdMap.get(list.get(i));
						if (w == null) {
							//System.out.println("===========188 ERROR=======:"+list.get(i));
							continue;
						}
						//FIXME 188行，报java.lang.NullPointerException
						String catePicIdPath = w.getCate()+"#"+w.getPicId();
						sb.append("\"").append(catePicIdPath).append("\",");
					}
					if (sb.charAt(sb.length()-1) == ',') {
						sb.delete(sb.length()-1,sb.length());
					}
					sb.append("]");
					starJson = sb.toString();//((BasicBSONList)o.get("star")).toString();
				}
			}
		} catch (Exception e) {
			System.out.println("------"+new Date());
			e.printStackTrace();
			return "";
		}
		return starJson;
	}
	
	public final String getCatePicId(String pic_oid){
		WallPic w = this.objIdMap.get(pic_oid);
		if (w == null) {
			return "";
		}
		return w.getCate()+"#"+w.getPicId();
	}
	
	/**
	 * 加星操作
	 * @param cate 对应wallPic表中的cate字段
	 * @param picId 对应wallPic表中的picId字段
	 * @param imei
	 * @return
	 */
	public boolean addStar(String picOid,String imei){
		//验证imei
		if (!checkIMEI(imei)) {
			return false;
		}
		try {
			//更新objIdMap缓存的图片加星数
			if (objIdMap != null) {
				WallPic pic = this.objIdMap.get(picOid);
				pic.setStars(pic.getStars()+1);
			}
			
			
			//更新wallUser表
			DBCollection coll = mongoCol.getColl("wallUser");
			BasicDBObject q = new BasicDBObject("imei",imei);
			DBCursor cur  = coll.find(q);
			if (cur.hasNext()) {
				DBObject user = cur.next();
				BasicBSONList list = (BasicBSONList) user.get("star");
				if (list.contains(picOid)) {
					return true;
				}
				
				BasicDBObject set = new BasicDBObject("$push",new BasicDBObject("star",picOid));
				coll.update(q, set);
			}
			
			//更新wallPic的加星数
			coll = mongoCol.getColl("wallPic");
			q = new BasicDBObject("_id",new ObjectId(picOid));
			BasicDBObject set = new BasicDBObject("$push",new BasicDBObject("starInfo",new BasicDBObject("imei",imei).append("msg", "").append("good", 0).append("bad", 0)));
			set.append("$inc", new BasicDBObject("stars",1));
			coll.update(q, set);
		} catch (Exception e) {
			System.out.println("------"+new Date());e.printStackTrace();
			return false;
		}
		this.hasNewStar = true;
		return true;
	}
	
//	public final String getObjId(String url){
//		String cate = "";
//		String picId = "";
//		String s = url.substring(url.lastIndexOf("/")+1,url.lastIndexOf("."));
//
//		Pattern pattern = Pattern.compile("([a-z]{2,})(\\d+)");
//		Matcher matcher = pattern.matcher(s);
//		if (matcher.find()) {
//			cate = matcher.group(1);
//			picId = matcher.group(2);
//		}
//		return (String)objIdMap.get(cate+"#"+picId);
//	}
	
	/**
	 * 取消加星
	 * @param cate 对应wallPic表中的cate字段
	 * @param picId 对应wallPic表中的picId字段
	 * @param imei
	 * @return
	 */
	public boolean cancelStar(String picOid,String imei){
		if (!checkIMEI(imei)) {
			return false;
		}
		try {
			
			//更新wallUser表
			DBCollection coll = mongoCol.getColl("wallUser");
			BasicDBObject q = new BasicDBObject("imei",imei);
			BasicDBObject set = new BasicDBObject("$pull",new BasicDBObject("star",picOid));
			coll.update(q, set);
			
			//更新wallPic的加星数
//			ObjectId oid = new ObjectId((String)objIdMap.get(cate+"#"+picId));
//			if (oid != null) {
				coll = mongoCol.getColl("wallPic");
				q = new BasicDBObject("_id",picOid).append("starInfo.imei", imei);
				set = new BasicDBObject("$inc", new BasicDBObject("download",1));
				coll.update(q, set);
//			}else{
//				System.out.println("--del--ObjectId can not found, cate:"+cate+" picId:"+picId);
//			}
			
			
		} catch (Exception e) {
			System.out.println("------"+new Date());e.printStackTrace();
			return false;
		}
		return true;
	}
	
	
	/**
	 * wallPic的下载数加一
	 * @param cate 对应wallPic表中的cate字段
	 * @param picId 对应wallPic表中的picId字段
	 * @param imei
	 * @return
	 */
	public boolean addDown(String picOid,String imei){
		
		if (!checkIMEI(imei) || picOid.trim().length() < 5) {
			return false;
		}
		try {
			
			//更新objIdMap缓存的图片加星数
			if ( this.objIdMap != null) {
				WallPic pic = this.objIdMap.get(picOid);
				pic.setDownload(pic.getDownload()+1);
			}
			
			
			DBCollection coll = mongoCol.getColl("wallPic");
			ObjectId oid = new ObjectId(picOid);
			BasicDBObject q = new BasicDBObject("_id",oid);
			BasicDBObject set = new BasicDBObject("$inc",new BasicDBObject("download",1));
			coll.update(q, set);
			
		} catch (Exception e) {
			System.out.println("picOid:"+picOid);
			System.out.println("------"+new Date());e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * 验证IMEI是否有效
	 * @param imei
	 * @return
	 */
	public static final boolean checkIMEI(String imei){
		if (imei == null || imei.length()<5 || imei.equals("000000000000000")) {
			return false;
		}
		return true;
	}
	
	/**
	 * 用户登录
	 */
	public final void login(Map args){
		
		//处理用户信息
		//imei作为查询条件
		if (args.get("imei") == null ) {
			return;
		}
		String imei = (String)args.get("imei");
		//验证imei是否存在
		if (imei.length() < 5) {
			return;
		}
		try {
			BasicDBObject q = new BasicDBObject();
			q.put("imei", imei);
			
			BasicDBObject user = new BasicDBObject();
			user.put("userName", (args.containsKey("userName"))?args.get("userName").toString():"");
			user.put("imsi", (args.containsKey("imsi"))?args.get("imsi").toString():"");
			BasicDBObject screen = new BasicDBObject();
			//FIXME 处理小数的情况
			try {
				screen.append("width",Integer.parseInt(args.get("width").toString())).append("height", Integer.parseInt(args.get("height").toString())).append("dpi", args.get("dpi").toString());
			} catch (Exception e) {
				System.out.println("---------");
				System.out.println("width:"+args.get("width").toString());
				System.out.println("height:"+args.get("height").toString());
				System.out.println("dpi:"+args.get("dpi").toString());
				System.out.println("---------");
				e.printStackTrace();
			}
			user.put("screen", screen);
			BasicDBObject handset = new BasicDBObject();
			handset.append("display", (args.get("display")==null)?"":args.get("display").toString())
			.append("board", (args.get("board")==null)?"":args.get("board").toString())
			.append("brand", (args.get("brand")==null)?"":args.get("brand").toString())
			.append("fingerprint", (args.get("fingerprint")==null)?"":args.get("fingerprint").toString())
			.append("device", (args.get("device")==null)?"":args.get("device").toString())
			.append("host", (args.get("host")==null)?"":args.get("host").toString())
			.append("id",(args.get("id")==null)?"": args.get("id").toString())
			.append("model", (args.get("model")==null)?"":args.get("model").toString())
			.append("product", (args.get("product")==null)?"":args.get("product").toString())
			.append("tags", (args.get("tags")==null)?"":args.get("tags").toString())
			.append("type", (args.get("type")==null)?"":args.get("type").toString())
			.append("user", (args.get("user")==null)?"":args.get("user").toString())
			.append("user-agent", (args.get("user-agent")==null)?"":args.get("user-agent").toString());
			user.put("handset", handset);
			
			user.put("appVersion", args.get("appVersion").toString());
			user.put("lastLogin", new Date());
			user.put("lang", args.get("lang").toString());
			user.put("ip", args.get("ip").toString());
			
			BasicDBObject set = new BasicDBObject();
			DBCollection coll = mongoCol.getColl("wallUser");
			DBCursor cur = coll.find(q);
			if (cur.hasNext()) {
				//老用户登录
				set.put("$inc",new BasicDBObject().append("loginTimes", 1));
			}else{
				//新用户注册
				user.put("regTime", new Date());
				user.put("loginTimes", 1);
				user.put("state", 1);
				user.put("info", "");
				user.put("star", new BasicBSONList());
				user.put("imei", imei);
			}
			
			set.put("$set", user);
			coll.update(q, set, true, false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 更新wallConfig的最新更新时间
	 * @param config DBObject
	 * @param updateStr 如update,updateJP,updateCN等
	 * @param lastUpdateTime 如2010-08-11
	 */
	private final void updateConfigTime(DBObject config,String updateStr,String lastUpdateTime){
		String s = (String)config.get(updateStr);
		s = s.split(":")[0]+":"+lastUpdateTime;
		config.put(updateStr, s);
	}
	
//	/**
//	 * 更新wallConfig的某一类的maxPic值
//	 * @param bsonList config中的index节点
//	 * @param config
//	 * @param catePre
//	 * @param maxPic
//	 */
//	private final void updateCateMaxPic(BasicBSONList bsonList,DBObject config,String catePre,int maxPic){
//		//第0个为最新，跳过从1开始
//		for (int i = 1; i < bsonList.size(); i++) {
//			BasicDBObject o = (BasicDBObject)bsonList.get(i);
//			if (((String) o.get("picPre")).equals(catePre)) {
//				o.put("maxPic", maxPic);
//				return;
//			}
//		}
//	}
	
	@SuppressWarnings("unchecked")
	final Map<String, Object> readJsonIni(String iniPath,String encode) {
		try {
			String str = IO.readTxt(iniPath, encode);
			Map<String, Object> json = (Map<String, Object>) jsonReader.read(str);
			return json;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}
	
	/**
	 * 读取配置文件,一般是/WEB-INF/fw_ini.json
	 * @param iniFile json形式
	 * @param cacheIni 缓存
	 * @param cacheIniUS 非中文缓存
	 * @return 是否读取成功
	 */
	@SuppressWarnings("unchecked")
	final boolean readIni(String iniFile){
		this.pause = true;
		try {
			Map<String,Object> ini = this.readJsonIni(iniFile, "utf-8");
			if (ini == null) {
				return false;
			}
			
			//其他配置
			this.k_woobooADcent = Integer.parseInt(ini.get("k_wooboocent").toString());
			this.s_woobooADcent = Integer.parseInt(ini.get("s_wooboocent").toString());
			this.dayUpdateMin = Integer.parseInt(ini.get("dayUpdateMin").toString());
			this.dayUpdateHour = Integer.parseInt(ini.get("dayUpdateHour").toString());
			this.newPicsOneDay = Integer.parseInt(ini.get("newPicsOneDay").toString());
			this.indexJsonPath = iniFile.substring(0,iniFile.lastIndexOf("/")+1)+ini.get("fwIndex").toString();
			this.cacheIniPath = iniFile.substring(0,iniFile.lastIndexOf("/")+1)+ini.get("cacheIniPath").toString();
			this.cacheIniPath_us = iniFile.substring(0,iniFile.lastIndexOf("/")+1)+ini.get("cacheIniPath_us").toString();
			
			System.out.println("cacheIniPath:"+cacheIniPath);
			
			//读取索引配置文件
			this.preConfig = IO.readTxt(this.indexJsonPath, "UTF-8");
			System.out.println("indexJsonPath:"+indexJsonPath);
			String ci = IO.readTxt(this.cacheIniPath, "utf-8");
			String cius = IO.readTxt(this.cacheIniPath_us, "utf-8");
			if (ci == null || cius == null || ci.length()<10 || cius.length()<10) {
				System.out.println("cache file error. skiped...");
			}else{
				this.wallconfig = ci;
				this.wallconfig_us = cius;
			}
			
			
			//数据库配置
			Map<String,String> mongoIni = (Map<String, String>) ini.get("mongo");
			String mongoIp = mongoIni.get("ip");
			int mongoPort = Integer.parseInt(mongoIni.get("port"));
			String mongoDb = mongoIni.get("db");
			String mongoUser = mongoIni.get("user");
			String mongoPwd = mongoIni.get("pwd");
			int maxWaitTime = Integer.parseInt(mongoIni.get("maxWaitTime"));
			int connectionsPerHost = Integer.parseInt(mongoIni.get("connectionsPerHost"));
			int threadsAllowedToBlockForConnectionMultiplier = Integer.parseInt(mongoIni.get("threadsAllowedToBlockForConnectionMultiplier"));
			if (this.mongoCol != null) {
				this.mongoCol.close();
			}
			this.mongoCol = new MongoCol(mongoIp, mongoPort, mongoDb, mongoUser, mongoPwd,maxWaitTime,connectionsPerHost,threadsAllowedToBlockForConnectionMultiplier);
		} catch (Exception e) {
			this.pause = false;
			e.printStackTrace();
			return false;
		}
		this.pause = false;
		return true;
	}
	
	final void exit(){
		try {
			IO.writeTxt(this.wallconfig, "utf-8", this.cacheIniPath);
			IO.writeTxt(this.wallconfig_us, "utf-8", this.cacheIniPath_us);
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.setRun(false);
		
	}
	
	/**
	 * 初始化
	 * @return
	 */
	final boolean init(){
		this.pause = true;
		try {
			//用于生成最新图的ArrayList
			this.picList = new ArrayList<WallPic>();
			DBCollection picColl = mongoCol.getColl("wallPic");
			//生成oid:WallPic缓存
			
			long pic_count = picColl.count();
			int findtime = Integer.parseInt(pic_count+"")/100+1;
			System.out.println("pic_count:"+pic_count+ " findtime:"+findtime);
			//int ii = 0;
			//100个一次地读取
			BasicDBObject q = new BasicDBObject("state",1);
			for (int i = 0; i < findtime; i++) {
				DBCursor ccur = picColl.find(q).sort(new BasicDBObject("_id",-1)).skip(i*100).limit(100);
				while (ccur.hasNext()) {
					DBObject o = ccur.next();
					String oid = ((ObjectId) (o.get("_id"))).toString();
					WallPic pic = new WallPic();
					pic.set_id(oid);
					pic.setAddTime(((Date)o.get("addTime")).getTime());
					pic.setCate((String)o.get("cate"));
					pic.setClick((Integer)o.get("click"));
					pic.setDownload((Integer)o.get("download"));
					pic.setInfo((String)o.get("info"));
					pic.setPicId((Integer)o.get("picId"));
					pic.setPicName((String)o.get("picName"));
					BasicBSONList parr = (BasicBSONList)o.get("picPath");
					//这里将第一个path放置oid的String
					int size = parr.size();
					String[] pathArr = new String[size+1];
					pathArr[0] = oid;
					for (int j = 0; j < size; j++) {
						pathArr[j+1] = (String) parr.get(j);
					}
//					int size = parr.size();
//					String[] paths = new String[size];
//					for (int j = 0; j < size; j++) {
//						paths[j] = (String) parr.get(j);
//					}
					pic.setPicPath(pathArr);
					pic.setPicSource((String)o.get("picSource"));
					pic.setSetWall((Integer)o.get("setWall"));
					pic.setStars((Integer)o.get("stars"));
					pic.setState((Integer)o.get("state"));
					pic.setTopId((Integer)o.get("topId"));
					objIdMap.put(oid,pic);
					if (pic.getState() == 1) {
						picList.add(pic);
					}
//					ii++;
//					if (ii%100 == 0) {
//						System.out.println("objIdMap building:"+ii);
//					}
				}
				System.out.println("objIdMap building:"+i);
			}
			
			
			//System.out.println("objIdMap built:"+ii);
			System.out.println("picList built:"+picList.size());
			
			//-----------------------------------------------------------------------------------
			
			
			//DBCursor cur = coll.find();
			//先由配置文件转换到DBObject
			DBObject config = (DBObject) JSON.parse(this.preConfig);
			
			//更新config的最新更新时间
			DBCollection coll_day = mongoCol.getColl("wallDay");
			DBCursor cur_day = coll_day.find(new BasicDBObject("id",1));
			if (cur_day.hasNext()) {
				DBObject dbo = (DBObject) cur_day.next();
				this.lastUpdate = (Date) dbo.get("lastUpdate");
			}
			String lastUpdateTime = StringUnit.getTime("yyyy-MM-dd", this.lastUpdate);
			updateConfigTime(config,"update",lastUpdateTime);
			updateConfigTime(config,"updateCN",lastUpdateTime);
			updateConfigTime(config,"updateEN",lastUpdateTime);
			updateConfigTime(config,"updateJP",lastUpdateTime);
			updateConfigTime(config,"updateTW",lastUpdateTime);

			//-----------------------------------------------------------------------------------
			
			
			//从数据库读取类别
			DBCollection cateColl = mongoCol.getColl("wallCate");
			DBCursor cur = cateColl.find(new BasicDBObject("state",1)).sort(new BasicDBObject("sortId",1));
			//载入所有的cate
			this.cateList = new ArrayList<WallCate>();
			while (cur.hasNext()) {
				DBObject o = cur.next();
				//String cate = (String) o.get("cateName");
				String catePre = (String) o.get("catePre");
				String cateName = (String)o.get("cateName");
				String cateCN = (String)o.get("cn");
				String cateEN = (String)o.get("en");
				String cateJP = (String)o.get("jp");
				String cateTW = (String)o.get("tw");
				long addTime = ((Date)o.get("addTime")).getTime();
				String cateSub = o.get("sub").toString();
				String cateStyle = o.get("style").toString();
				int maxPic = Integer.parseInt(o.get("max").toString());
				WallCate cate = new WallCate();
				cate.setAddTime(addTime);
				cate.setCateName(cateName);
				cate.setCn(cateCN);
				cate.setEn(cateEN);
				cate.setJp(cateJP);
				cate.setTw(cateTW);
				cate.setInfo(o.get("info").toString());
				cate.setCatePre(catePre);
				cate.setSub(cateSub);
				cate.setStyle(cateStyle);
				cate.setSortId(Integer.parseInt(o.get("sortId").toString()));
				cate.setMax(maxPic);
				//state均为1
				cate.setState(1);
				cateList.add(cate);
			}
			System.out.println("=======wallCate fromDB loaded========");
			
			//-----------------------------------------------------------------------------------
			
			//按topId和addTime倒序排
			Collections.sort(picList, Collections.reverseOrder());
			
			//生成前50页最新图的列表
			//DBCursor newCur = picColl.find(new BasicDBObject("topId",1).append("state", 1)).sort(new BasicDBObject("addTime",-1)).limit(60*4);
			//索引中的index节点
			BasicBSONList index = new BasicBSONList();
			//最新中的pics节点
			BasicBSONList pics = new BasicBSONList();
			//每一页的图片数组
			BasicBSONList pageIndex = new BasicBSONList();
			
			//前60页,i为每页的计数点,p为页数
			int i = 0,p = 1;
			for (Iterator<WallPic> it = picList.iterator(); it.hasNext();) {
				WallPic pic = it.next();
				pageIndex.put(i, pic.getCate()+"#"+pic.getPicId());
				if (i == 3) {
					i = 0;
					pics.add(pageIndex);
					pageIndex = new BasicBSONList();
					p++;
				}else{
					i++;
				}
				if (p > 60) {
					break;
				}
			}
			
//			while (newCur.hasNext()) {
//				DBObject newobj = newCur.next();
//				//使用cate+#+picId
//				String s = (String)newobj.get("cate")+"#"+(Integer) newobj.get("picId");
//				pageIndex.put(i, s);
//				if (i == 3) {
//					i = 0;
//					//j为新增的页数
//					//j++;
//					//加到数组末尾
//					pics.add(pageIndex);
//					pageIndex = new BasicBSONList();
//				}else{
//					i++;
//				}
//				
//			}
			//index中的第一个位置加入最新节点
			BasicDBObject picsNode = new BasicDBObject();
			picsNode.put("cate", "Newest");
			picsNode.put("cateCN", "最新");
			picsNode.put("cateEN", "Newest");
			picsNode.put("cateJP", "最新");
			picsNode.put("cateTW", "最新");
			picsNode.put("pics", pics);
			index.put("0", picsNode);
			
			//-----------------------------------------------------------------------------------
	
			
			
			//用于生成排序索引mMap
			HashMap<String,ArrayList<String[]>> mMap = new HashMap<String, ArrayList<String[]>>(100);
			HashMap<String,Integer> ccMap = new HashMap<String, Integer>();
			
			for (Iterator<WallCate> it = cateList.iterator(); it.hasNext();) {
				WallCate cate = it.next();
				//生成该类的节点
				DBObject cateNode = new BasicDBObject();
				cateNode.put("cate", cate.getCateName());
				cateNode.put("picPre", cate.getCatePre());
				cateNode.put("cateCN", cate.getCn());
				cateNode.put("cateEN", cate.getEn());
				cateNode.put("cateJP", cate.getJp());
				cateNode.put("cateTW", cate.getTw());
				cateNode.put("maxPic", cate.getMax());
				cateNode.put("sub", cate.getSub());
				cateNode.put("style", cate.getStyle());
				index.add(cateNode);
				//以时间为序
//				Collections.sort(picList, Collections.reverseOrder());
				Collections.sort(picList);
				ArrayList<String[]> timeList =readListFromPicList(cate.getCatePre(),picList);
				if (timeList.size() > 0) {
					mMap.put(cate.getCatePre()+"#time", timeList);
				}
				//以下载量为序
//				Comparator<WallPic> downDescComparator = Collections.reverseOrder(new picDownComparator());
				Collections.sort(picList, new picDownComparator());
				ArrayList<String[]> downList = readListFromPicList(cate.getCatePre(),picList);
				if (downList.size() > 0) {
					mMap.put(cate.getCatePre()+"#down", timeList);
				}
				//以加星量为序 ,需要定期更新索引
//				Comparator<WallPic> starDescComparator = Collections.reverseOrder(new picStarComparator());
				Collections.sort(picList, new picStarComparator());
				ArrayList<String[]> starList = readListFromPicList(cate.getCatePre(),picList);
				if (starList.size() > 0) {
					mMap.put(cate.getCatePre()+"#star", starList);
				}
				ccMap.put(cate.getCatePre(), timeList.size());
				System.out.println(cate.getCatePre()+":"+timeList.size());
				
			}
			
			/*
			
			while (cur.hasNext()) {
				DBObject o = cur.next();
				//String cate = (String) o.get("cateName");
				String catePre = (String) o.get("catePre");
				String cateName = (String)o.get("cateName");
				String cateCN = (String)o.get("cn");
				String cateEN = (String)o.get("en");
				String cateJP = (String)o.get("jp");
				String cateTW = (String)o.get("tw");
				String cateSub = o.get("sub").toString();
				String cateStyle = o.get("style").toString();
				int maxPic = Integer.parseInt(o.get("max").toString());
				//更新config中该类的maxPic
				if (config != null) {
					DBCursor pcur = picColl.find(new BasicDBObject("cate",catePre).append("state", 1)).sort(new BasicDBObject("picId",-1)).limit(1);
					if (pcur.hasNext()) {
						maxPic = (Integer)((DBObject)pcur.next()).get("picId");
					}
				}
				//生成该类的节点
				DBObject cateNode = new BasicDBObject();
				cateNode.put("cate", cateName);
				cateNode.put("picPre", catePre);
				cateNode.put("cateCN", cateCN);
				cateNode.put("cateEN", cateEN);
				cateNode.put("cateJP", cateJP);
				cateNode.put("cateTW", cateTW);
				cateNode.put("maxPic", maxPic);
				cateNode.put("sub", cateSub);
				cateNode.put("style", cateStyle);
				index.add(cateNode);
				
				//以时间为序
				DBCursor pcur = picColl.find(new BasicDBObject("cate",catePre).append("state", 1)).sort(new BasicDBObject("picId",1));
				ArrayList<String[]> timeList = this.readListFromColl(pcur);
				if (timeList.size() > 0) {
					mMap.put(catePre+"#time", timeList);
//					updateCateMaxPic(config,catePre,(timeList.get(timeList.size()-1)));
				}
				
				//以下载量为序
				pcur = picColl.find(new BasicDBObject("cate",catePre).append("state", 1)).sort(new BasicDBObject("download",1));
				ArrayList<String[]> downList = this.readListFromColl(pcur);
				if (downList.size() > 0) {
					mMap.put(catePre+"#down", downList);
				}
//				System.out.println(catePre+"#down:"+downList.size());
				
				//以加星量为序 ,需要定期更新索引
				pcur = picColl.find(new BasicDBObject("cate",catePre).append("state", 1)).sort(new BasicDBObject("stars",1).append("picId", -1));
				ArrayList<String[]> starList = this.readListFromColl(pcur);
				if (starList.size() > 0) {
					mMap.put(catePre+"#star", starList);
				}
//				System.out.println(catePre+"#star:"+starList.size());
				ccMap.put(catePre, timeList.size());
				System.out.println(catePre+":"+timeList.size());
			}*/
			
			
			//汇总并更新picCateMap
			if (mMap.size()>0) {
				this.picCateMap = mMap;
				this.cateCountMap = ccMap;
				System.out.println("=======picCateMap loaded========");
			}
			//最后更新一次wallConfig
			config.put("index", index);
			this.wallconfig = config.toString();
			
			//生成非中文服务器的wallConfig
			Object ous = config.get("server_us");
			if (ous != null) {
				BasicBSONList usList = (BasicBSONList) ous;
				BasicBSONList cnServers = (BasicBSONList)config.get("servers");
				usList.addAll(cnServers);
				config.put("servers", usList);
			}
			this.wallconfig_us = config.toString();
			System.out.println("wallconfig_us built.");
			
			this.checkReIndexTime();
			//获取日更新时间
			this.nextUpdateTime = this.getNextDayUpdateTime();
			
			System.out.println("=======nextReIndexStarTime:"+this.nextReIndexStarTime+"=====");
			System.out.println("=======nextUpdateTime:"+this.nextUpdateTime+" now:"+new Date()+"=====");
			
		} catch (Exception e) {
			System.out.println("------"+new Date());e.printStackTrace();
			return false;
		}
		
		this.pause = false;
		
		return true;
	}
	
	
	/**
	 * @return the pause
	 */
	public final boolean isPause() {
		return pause;
	}


	/**
	 * 图片下载量排序器
	 * @author keel
	 *
	 */
	class picDownComparator implements Comparator<WallPic> {

		public int compare(WallPic w1, WallPic w2) {
			return w1.getDownload() - w2.getDownload();
		}
	}
	
	/**
	 * 图片加星量排序器
	 * @author keel
	 *
	 */
	class picStarComparator implements Comparator<WallPic> {

		public int compare(WallPic w1, WallPic w2) {
			return w1.getStars() - w2.getStars();
		}
	}
	
	private final void checkReIndexTime(){
		Calendar c = Calendar.getInstance(timeZone);
		c.add(Calendar.SECOND, this.starSleep);
		this.nextReIndexStarTime = c.getTime();
	}
	
	private final void checkReIndex(){
		if (new Date().after(nextReIndexStarTime)) {
			//处理加星
			if (hasNewStar) {
				this.hasNewStar = false;
				this.reIndex("star");
			}
			//处理下载
			this.reIndex("down");
			this.checkReIndexTime();
		}
	}
	
	private final void reIndex(String tag){
		try {
//			DBCollection picColl = mongoCol.getColl("wallPic");
//			DBCollection cateColl = mongoCol.getColl("wallCate");
//			DBCursor cur = cateColl.find(new BasicDBObject("state",1));
//			int sort = -1;
//			if (asc) {
//				sort = 1;
//			}
//			while (cur.hasNext()) {
//				DBObject o = cur.next();
//				String catePre = (String) o.get("catePre");
//				//以加星量为序 
//				DBCursor pcur = picColl.find(new BasicDBObject("cate",catePre).append("state", 1)).sort(new BasicDBObject(orderby,sort).append("picId", -1));
//				ArrayList<String[]> starList = this.readListFromColl(pcur);
//				if (starList.size() > 0) {
//					this.picCateMap.put(catePre+"#"+tag, starList);
//				}
//			}
			//System.out.println("=======reIndexStar OK!=========");
			for (Iterator<WallCate> it = this.cateList.iterator(); it.hasNext();) {
				WallCate cate = it.next();
				//以下载量为序
				if (tag.equals("down")) {
//					Comparator<WallPic> downDescComparator = Collections.reverseOrder(new picDownComparator());
					Collections.sort(picList, new picDownComparator());
					ArrayList<String[]> downList = readListFromPicList(cate.getCatePre(),picList);
					if (downList.size() > 0) {
						this.picCateMap.put(cate.getCatePre()+"#"+tag, downList);
					}
				}else if (tag.equals("star")){
//					Comparator<WallPic> starDescComparator = Collections.reverseOrder(new picStarComparator());
					Collections.sort(picList, new picStarComparator());
					ArrayList<String[]> starList = readListFromPicList(cate.getCatePre(),picList);
					if (starList.size() > 0) {
						this.picCateMap.put(cate.getCatePre()+"#"+tag, starList);
					}
				}
			}
		} catch (Exception e) {
			System.out.println("=======reIndex ERROR:"+tag);
			System.out.println("------"+new Date());
			e.printStackTrace();
		}
	}
	
//	private final void reIndexStar(){
//		try {
//			DBCollection picColl = mongoCol.getColl("wallPic");
//			DBCollection cateColl = mongoCol.getColl("wallCate");
//			DBCursor cur = cateColl.find(new BasicDBObject("state",1));
//			while (cur.hasNext()) {
//				DBObject o = cur.next();
//				String catePre = (String) o.get("catePre");
//				//以加星量为序 
//				DBCursor pcur = picColl.find(new BasicDBObject("cate",catePre).append("state", 1)).sort(new BasicDBObject("stars",1).append("picId", -1));
//				ArrayList<String[]> starList = this.readListFromColl(pcur);
//				if (starList.size() > 0) {
//					this.picCateMap.put(catePre+"#star", starList);
//				}
//			}
//			//System.out.println("=======reIndexStar OK!=========");
//			
//		} catch (Exception e) {
//			System.out.println("=======reIndexStar ERROR!=========");
//			System.out.println("------"+new Date());e.printStackTrace();
//		}
//	}
	
//	private final ArrayList<String[]> readListFromColl(DBCursor pcur){
//		ArrayList<String[]> cateList = new ArrayList<String[]>(2000);
//		while (pcur.hasNext()) {
//			DBObject o = pcur.next();
//			BasicBSONList paths = (BasicBSONList)o.get("picPath");
//			String oid = ((ObjectId) o.get("_id")).toString();
//			
//			//第一个位置给ObjectId
//			int size = paths.size();
//			String[] pathArr = new String[size+1];
//			pathArr[0] = oid;
//			for (int i = 0; i < size; i++) {
//				pathArr[i+1] = (String) paths.get(i);
//			}
//			cateList.add(pathArr);
//		}
//		return cateList;
//	}
	
	/**
	 * 读取对应类别的paths
	 * @param catePre
	 * @param picList ArrayList<WallPic>
	 * @return
	 */
	private final ArrayList<String[]> readListFromPicList(String catePre,ArrayList<WallPic> picList){
		ArrayList<String[]> cateList = new ArrayList<String[]>(3000);
		for (Iterator<WallPic> it = picList.iterator(); it.hasNext();) {
			WallPic pic = it.next();
			if (pic.getCate().equals(catePre)) {
				cateList.add(pic.getPicPath());
			}
		}
		
		return cateList;
	}
	
//	void test(){
//		String url = "/orion/ava/b_ava12.jpg";
//		System.out.println(this.getPicFromUrl(url, "time", 1));
		
//		ObjectId oid = new ObjectId("4c3932a0a79f0aef53a2cbb4");
//		String imei = "abcde";
//		DBCollection coll = mongoCol.getColl("wallPic");
//
//		this.cancelStar("4c3932a0a79f0aef53a2cbb4", imei);
//		
//		DBCursor cur = coll.find(new BasicDBObject("_id",oid));
//		if (cur.hasNext()) {
//			System.out.println(cur.next());
//		}
//		this.cancelStar("4c3932a0a79f0aef53a2cbb4", imei);
//		String s = this.getStarIndexByUser(imei);
//		System.out.println(s);
		
		
		
//		DBCollection coll = mongoCol.getColl("wallPic");
//		DBCursor cur = coll.find();
//		if (cur.hasNext()) {
//			DBObject o = cur.next();
//			String s = StringUnit.getTime("yyyy-MM-dd", (Date)o.get("addTime"));
//			System.out.println(s);
//		}
//		mongoCol.close();
//	}
	
	
	/**
	 * 添加一个处理新图的任务
	 * @return String 新任务的ObjectId
	 */
	public final String addNewPicsTask(){
		DBCollection coll = mongoCol.getColl("wallTask");
		BasicDBObject t = new BasicDBObject();
		ObjectId oid = new ObjectId();
		t.put("_id", oid);
		t.put("task", 2);
		t.put("state", 1);
		t.put("addTime", new Date());
		coll.insert(t);
		return oid.toString();
	}

	
	@SuppressWarnings("unchecked")
	public final void addTask(Map reqMap){
		this.taskList.add(reqMap);
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		try {
			//先初始化
			if(!this.init()){
				return;
			}
			while (isRun) {
				
				if (!pause) {
					try {
						//执行任务
						if (!this.taskList.isEmpty()) {
							Map task = (Map) this.taskList.remove(0);
							//目前只有登录请求任务，直接执行
							this.login(task);
						}
						//处理索引更新
						this.checkReIndex();
						//处理每天的更新图
						this.updateDaily();
//						updateByDay();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			
				//sleep暂为1秒
				Thread.sleep(5000);
			}
			if (mongoCol != null) {
				mongoCol.close();
			}
			
		} catch (InterruptedException e) {
			if (mongoCol != null) {
				mongoCol.close();
			}
			System.out.println("------"+new Date());e.printStackTrace();
		}
	}
	
	/**
	 * 每日更新中的上次更新时间
	 */
	private Date nextUpdateTime;
	
	/**
	 * 上次有更新时的数据刷新时间
	 */
	private Date lastUpdate;
	
	/**
	 * @return nextUpdateTime
	 */
	public Date getNextDayUpdate(){
		return this.nextUpdateTime;
	}
	
	/**
	 * 从DB获 取下次更新时间
	 * @return 如果获取不到则返回当前时间的后一天
	 */
	private final Date getNextDayUpdateTime(){
		Calendar c = Calendar.getInstance(timeZone);
		if (c.get(Calendar.HOUR_OF_DAY) > this.dayUpdateHour || (c.get(Calendar.HOUR_OF_DAY) == this.dayUpdateHour && c.get(Calendar.MINUTE) >= this.dayUpdateMin)) {
			//推到明天
			c.add(Calendar.DATE, +1);
		}
		c.set(Calendar.HOUR_OF_DAY, this.dayUpdateHour);
		c.set(Calendar.MINUTE, this.dayUpdateMin);
		return c.getTime();
		/*
		DBCollection coll = mongoCol.getColl("wallDay");
		DBCursor cur = coll.find(new BasicDBObject("id",1));
		if (cur.hasNext()) {
			Calendar c = Calendar.getInstance(timeZone);
			c.setTime((Date)cur.next().get("nextUpdateTime"));
			c.set(Calendar.HOUR_OF_DAY, this.dayUpdateHour);
			c.set(Calendar.MINUTE, this.dayUpdateMin);
			return c.getTime();
		}else{
			Calendar c = Calendar.getInstance(timeZone);
			c.set(Calendar.HOUR_OF_DAY, this.dayUpdateHour);
			c.set(Calendar.MINUTE, this.dayUpdateMin);
			c.add(Calendar.DATE, +1);
			return c.getTime();
		}*/
	}
	
	/**
	 * 每天更新的图片数量
	 */
	private int newPicsOneDay = 4;
	
	/**
	 * @return the newPicsOneDay
	 */
	public final int getNewPicsOneDay() {
		return newPicsOneDay;
	}

	/**
	 * @param newPicsOneDay the newPicsOneDay to set
	 */
	public final void setNewPicsOneDay(int newPicsOneDay) {
		this.newPicsOneDay = newPicsOneDay;
	}

	public void updateDaily(){
		
		try {
			if (new Date().after(this.nextUpdateTime)) {
				System.out.println("---- updateByDay running... newPicsOneDay:"+this.newPicsOneDay+" thisUpdateTime:"+nextUpdateTime);
				Calendar c = Calendar.getInstance(timeZone);
				c.setTime(this.nextUpdateTime);
				c.add(Calendar.DATE, +1);
				this.nextUpdateTime = c.getTime();
				
				boolean needInit = false;
				int i = 0;
				//当更新数大于0时进行日更新操作
				if (this.newPicsOneDay > 0) {
					DBCollection coll_pic = mongoCol.getColl("wallPic");
					DBCollection coll_cate = mongoCol.getColl("wallCate");
					
					//将state为2的新图片按类别排序，再按picId顺序排，避免更新乱掉
					DBCursor cur = coll_pic.find(new BasicDBObject("state",2)).sort((new BasicDBObject("cate",1)).append("picId",1)).limit(this.newPicsOneDay);
					while (cur.hasNext()) {
						DBObject dbo = (DBObject) cur.next();
						ObjectId oid = (ObjectId) dbo.get("_id");
						//更新topId为1
						coll_pic.update(new BasicDBObject("_id",oid), new BasicDBObject("$set",new BasicDBObject("state",1).append("addTime", new Date())));
						//更新图片类别的max
						String cate = (String)dbo.get("cate");
						int picId = Integer.parseInt(dbo.get("picId").toString());
						coll_cate.update(new BasicDBObject("catePre",cate), new BasicDBObject("$set",new BasicDBObject("max",picId)));
						System.out.println("Update today pic:"+dbo.get("picName")+" id:"+oid);
						i++;
					}
					//有更新
					if (i > 0) {
						System.out.println("----Update by day pics:"+i);
						//重新初始化
						needInit = true;
					}	
				}
				System.out.println("---- updateByDay over... updated today:"+i+" nextUpdateTime:"+nextUpdateTime);
				//最后重新初始化
				if (needInit) {
					this.init();
				}
				
				
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (MongoException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 每天更新4张图片，将topId为2的标记为1，然后更新lastUpdateTime
	 * @throws InterruptedException 
	 */
	public void updateByDay(){
		try {
			Date now  = new Date();
			//System.out.println("---- updateByDay lastUpdate:"+this.lastUpdate+" now:"+now);
			//System.out.println("---- updateByDay nextUpdateTime:"+this.nextUpdateTime+" now:"+now);
			if (now.after(this.nextUpdateTime)) {
				System.out.println("---- updateByDay running... newPicsOneDay:"+this.newPicsOneDay+" nextUpdateTime:"+nextUpdateTime);
				
				//真实更新的图片数
				int i = 0;
				boolean needInit = false;
				//下次更新时间推后一天
				Calendar c = Calendar.getInstance(timeZone);
				c.setTime(nextUpdateTime);
				c.set(Calendar.HOUR_OF_DAY, this.dayUpdateHour);
				c.set(Calendar.MINUTE, this.dayUpdateMin);
				c.add(Calendar.DATE, 1);
				this.nextUpdateTime = c.getTime();
				System.out.println("---- nextUpdateTime:"+this.nextUpdateTime);
				
				//当更新数大于0时进行日更新操作
				if (this.newPicsOneDay > 0) {
					DBCollection coll_pic = mongoCol.getColl("wallPic");
					DBCollection coll_cate = mongoCol.getColl("wallCate");
					
					//将state为2的新图片按类别排序，再按picId顺序排，避免更新乱掉
					DBCursor cur = coll_pic.find(new BasicDBObject("state",2)).sort((new BasicDBObject("cate",1)).append("picId",1)).limit(this.newPicsOneDay);
					
					while (cur.hasNext()) {
						DBObject dbo = (DBObject) cur.next();
						ObjectId oid = (ObjectId) dbo.get("_id");
						//更新topId为1
						coll_pic.update(new BasicDBObject("_id",oid), new BasicDBObject("$set",new BasicDBObject("state",1).append("addTime", new Date())));
						//更新图片类别的max
						String cate = (String)dbo.get("cate");
						int picId = Integer.parseInt(dbo.get("picId").toString());
						coll_cate.update(new BasicDBObject("catePre",cate), new BasicDBObject("$set",new BasicDBObject("max",picId)));
						System.out.println("Update today pic:"+dbo.get("picName")+" id:"+oid);
						i++;
					}
					//有更新
					if (i > 0) {
//					this.wallconfig = config.toString();
						System.out.println("----Update by day pics:"+i);
						//重新初始化
						needInit = true;
						//this.init();
						//实始化会更新this.lastUpdate为数据库中的数据
						this.lastUpdate = new Date();
					}		
					
				}else{
					//延迟2分钟再等待主服务器数据更新后再更新数据 --通过配置文件中指定更新时间才实现延迟
//				try {
//					Thread.sleep(1000*60*2);
//				} catch (InterruptedException e) {
//					return;
//				}
					
					/*
					//是否数据有更新，如有则重新初始化
					DBCollection coll = mongoCol.getColl("wallDay");
					DBCursor cur = coll.find(new BasicDBObject("id",1));
					if (cur.hasNext()) {
						DBObject dbo = (DBObject) cur.next();
						Date lastUpdateT = (Date) dbo.get("lastUpdate");
						System.out.println("lastUpdateT:"+lastUpdateT);
						System.out.println("this.lastUpdate:"+this.lastUpdate);
						//如果本服务器的lastUpdate时间比数据库里的更新时间早，则进行init()
						if (this.lastUpdate != null && this.lastUpdate.before(lastUpdateT)) {
							this.lastUpdate = lastUpdateT;
							//重新初始化
							needInit = true;
						}else{
							this.lastUpdate = lastUpdateT;
						}
						
					}else{
						needInit = true;
						this.lastUpdate = new Date();
					}
					*/
					needInit = true;
					this.lastUpdate = new Date();
					
					System.out.println("----lastUpdate:"+this.lastUpdate);
				}

				//更新下次更新时间,当newPicsOneDay不为0时
				if (this.newPicsOneDay > 0) {
					//用于更新wallDay的数据
					BasicDBObject setData = new BasicDBObject("nextUpdateTime",this.nextUpdateTime);
					if (i > 0 ) {
						//本次有更新时
						setData.append("lastUpdate", this.lastUpdate);
					}
					DBCollection coll = mongoCol.getColl("wallDay");
					coll.update(new BasicDBObject("id",1), new BasicDBObject("$set",setData));
				}
				System.out.println("----next updateByDay:"+this.nextUpdateTime+ " lastUpdate:"+this.lastUpdate);
				//最后重新初始化
				if (needInit) {
					this.init();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return the wallconfig
	 */
	public String getWallconfig() {
		return wallconfig;
	}
	
	/**
	 * @return the wallconfig
	 */
	public String getWallconfig_us() {
		return wallconfig_us;
	}

	/**
	 * @return the isRun
	 */
	public boolean isRun() {
		return isRun;
	}

	/**
	 * 关闭连接并退出线程
	 * @param isRun the isRun to set
	 */
	public void setRun(boolean isRun) {
		if (!isRun) {
			mongoCol.close();
		}
		this.isRun = isRun;
	}

	/**
	 * 立即处理每日更新索引,通过调整nextUpdateTime到1小时前实现
	 */
	public String updateNewPicsNow(){
		String re = "fail";
		try {
			Calendar c = Calendar.getInstance(timeZone);
			c.add(Calendar.HOUR_OF_DAY, -1);
			DBCollection coll = mongoCol.getColl("wallDay");
			DBCursor cur = coll.find();
			if (cur.hasNext()) {
				DBObject o = cur.next();
				o.put("nextUpdateTime", c.getTime());
				//o.put("lastUpdate",new Date());
				coll.update(new BasicDBObject("id",1), o);
			}
			
			this.nextUpdateTime = c.getTime(); 
			return this.nextUpdateTime.toString();
		} catch (Exception e) {
			e.printStackTrace();
			return re;
		}
	}
	
	

	/**
	 * @return the mongoCol
	 */
	public final MongoCol getMongoCol() {
		return mongoCol;
	}



	/**
	 * @param args
	 */
	public static void main(String[] args) {
		FWall f = new FWall("f:/works/workspace_keel/orion/WebContent/WEB-INF/fw_ini.json");
		f.mongoCol.setIp("202.102.40.43");
		//DBCollection coll_cate = f.mongoCol.getColl("wallCate");
		/*
		
		coll_cate.update(new BasicDBObject("cateName","Abstract"),new BasicDBObject("$set",new BasicDBObject("sortId",10)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Space"),new BasicDBObject("$set",new BasicDBObject("sortId",20)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Scenic"),new BasicDBObject("$set",new BasicDBObject("sortId",30)),false,false);
		coll_cate.update(new BasicDBObject("cateName","People"),new BasicDBObject("$set",new BasicDBObject("sortId",40)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Movie"),new BasicDBObject("$set",new BasicDBObject("sortId",50)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Food"),new BasicDBObject("$set",new BasicDBObject("sortId",60)),false,false);
		coll_cate.update(new BasicDBObject("cateName","City"),new BasicDBObject("$set",new BasicDBObject("sortId",70)),false,false);
		coll_cate.update(new BasicDBObject("cateName","LOMO"),new BasicDBObject("$set",new BasicDBObject("sortId",80)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Creature"),new BasicDBObject("$set",new BasicDBObject("sortId",90)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Game"),new BasicDBObject("$set",new BasicDBObject("sortId",100)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Comic"),new BasicDBObject("$set",new BasicDBObject("sortId",110)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Car"),new BasicDBObject("$set",new BasicDBObject("sortId",120)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Childhood"),new BasicDBObject("$set",new BasicDBObject("sortId",130)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Still life"),new BasicDBObject("$set",new BasicDBObject("sortId",140)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Chinese style"),new BasicDBObject("$set",new BasicDBObject("sortId",150)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Flower"),new BasicDBObject("$set",new BasicDBObject("sortId",160)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Avatar"),new BasicDBObject("$set",new BasicDBObject("sortId",170)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Iceland"),new BasicDBObject("$set",new BasicDBObject("sortId",180)),false,false);
		coll_cate.update(new BasicDBObject("cateName","WorldCup2010"),new BasicDBObject("$set",new BasicDBObject("sortId",190)),false,false);
		
		coll_cate.update(new BasicDBObject("cateName","Easter Day"),new BasicDBObject("$set",new BasicDBObject("sortId",5)),false,false);
		coll_cate.update(new BasicDBObject("cateName","Halloween"),new BasicDBObject("$set",new BasicDBObject("sortId",4)),false,false);
		DBCursor cur = coll_cate.find().sort(new BasicDBObject("sortId",1));
		while (cur.hasNext()) {
			DBObject dbo = (DBObject) cur.next();
			System.out.println(dbo.get("cateName")+" "+dbo.get("sortId"));
		}
		*/
		
		/*
		Calendar c = Calendar.getInstance(timeZone);
		c.set(Calendar.HOUR_OF_DAY, 12);
		c.set(Calendar.MINUTE, 00);
		f.nextUpdateTime = c.getTime();
		f.setNewPicsOneDay(0);
		f.updateByDay();
		*/
		
//		DBCollection coll_pic = mongoCol.getColl("wallCate");
//		coll_pic.update(new BasicDBObject("catePre","hallo"), new BasicDBObject("$set",new BasicDBObject("state",0)));
		//System.out.println(re.getError());
		
		
//		f.init();
//		System.out.println(f.wallconfig);
		
//		DBCollection coll_cate = f.mongoCol.getColl("wallCate");
//		coll_cate.update(new BasicDBObject("catePre","japan"), new BasicDBObject("$set",new BasicDBObject("state",0)));
		
		DBCollection coll = f.mongoCol.getColl("wallPic");
		
		int t = 15;
		String cate = "abs";
		int begin = 203;
		int end = 206;
		
		
		Calendar c = Calendar.getInstance(timeZone);
		c.set(Calendar.HOUR_OF_DAY, t);
		BasicDBObject q = new BasicDBObject();
		q.append("state", 1).append("cate", cate).append("picId",new BasicDBObject("$gte",begin).append("$lte", end));
		BasicDBObject set = new BasicDBObject();
		set.append("$set", new BasicDBObject("addTime",c.getTime()));
		coll.update(q, set,false,true);
		
//		DBCursor cur = coll_cate.find();
//		while (cur.hasNext()) {
//			DBObject o = cur.next();
//			String cate = (String)o.get("catePre");
//			DBCursor cur2 =  coll_pic.find(new BasicDBObject("state",1).append("cate", cate),new BasicDBObject("picId",1)).sort(new BasicDBObject("picId",-1)).limit(1);
//			if (cur2.hasNext()) {
//				DBObject o2 = cur2.next();
//				int max = Integer.parseInt(o2.get("picId")+"");
//				System.out.println(cate+" max:"+max);
//				coll_cate.update(new BasicDBObject("catePre",cate), new BasicDBObject("$set",new BasicDBObject("max",max)));
//			}
//		}
		
//		coll_pic.remove(new BasicDBObject("cate","japan"));
		
		
//		f.init();
//		f.updateByDay();
		
		
//----------------------	
//生成每日更新时间的表
//		DBCollection coll = mongoCol.getColl("wallDay");
//		Calendar c = Calendar.getInstance(timeZone);
//		c.set(Calendar.HOUR_OF_DAY, 12);
//		c.set(Calendar.MINUTE, 00);
//		//c.add(Calendar.DATE, 1);
//		BasicDBObject o = new BasicDBObject();
//		o.put("id", 1);
//		o.put("nextUpdateTime", c.getTime());
//		coll.save(o);
//设置时间		
//		DBCollection coll = f.mongoCol.getColl("wallDay");
//		DBCursor cur = coll.find();
//		if (cur.hasNext()) {
//			DBObject o = cur.next();
//			Date d = (Date)o.get("nextUpdateTime");
//			Calendar c = Calendar.getInstance(timeZone);
//			c.set(Calendar.HOUR_OF_DAY, 12);
//			c.set(Calendar.MINUTE, 00);
//			//c.add(Calendar.DATE, 1);
//			o.put("nextUpdateTime", c.getTime());
//			o.put("lastUpdate",new Date());
//			coll.update(new BasicDBObject("id",1), o);
//		}
//----------------------
		
//		System.out.println(f.getWallconfig());
//		f.test();
//		String s =f.addNewPicsTask();
//		System.out.println(s);
	}

}
