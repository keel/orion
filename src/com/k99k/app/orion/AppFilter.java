package com.k99k.app.orion;

import java.io.IOException;
import java.util.HashMap;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.stringtree.json.JSONReader;
import org.stringtree.json.JSONValidatingReader;

import com.k99k.tools.IO;
import com.k99k.tools.encrypter.Encrypter;

/**
 * Servlet Filter implementation class AppFilter
 */
public class AppFilter implements Filter {

    /**
     * Default constructor. 
     */
    public AppFilter() {
    }

	/**
	 * @see Filter#destroy()
	 */
	public void destroy() {
		fwall.setRun(false);
	}
	
	private static FWall fwall;// = new FWall("/WEB-INF/fw_ini.json");
	
//	private boolean test = false;
	
	private final static JSONReader jsonReader = new JSONValidatingReader();
	
//	/**
//	 * 加密用的key
//	 * TODO 未实现密钥网络更新机制
//	 */
//	static final String encryptKey = "htHunter01_!(!)";
	
	/**
	 * 加密器
	 */
	static final Encrypter desEncrypt = createEncrypter();
	
	private final static Encrypter createEncrypter(){
		try {
			return new Encrypter();
		} catch (Exception e) {
		}
		return null;
	}

	/**
	 * @see Filter#doFilter(ServletRequest, ServletResponse, FilterChain)
	 */
	@SuppressWarnings("unchecked")
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest req = (HttpServletRequest)request;
		HttpServletResponse resp = (HttpServletResponse)response;
		resp.setCharacterEncoding("utf-8");
		resp.setContentType("text/html;charset=utf-8");
//		Enumeration heads = req.getHeaderNames();
//		System.out.println(req.getRequestURL());
//		System.out.println(req.getRemoteAddr());
//		StringBuilder sb = new StringBuilder();
//		while(heads.hasMoreElements())
//		{
//		   String item = (String)heads.nextElement();
//		   sb.append(item);
//		   sb.append(":");
//		   sb.append(req.getHeader(item)).append("\n");
//		}
//		System.out.println(sb);
		
		resp.setHeader("obj_id", "objectId");
		
		String url = req.getRequestURI();
		
		String pic_oid = "";
		String imei = "";
		String type = "";
		pic_oid = (req.getHeader("pic_oid") == null)?"":req.getHeader("pic_oid");
		imei = (req.getHeader("imei") == null || req.getHeader("imei").length()<5)?"":req.getHeader("imei");
		type = (req.getHeader("type") == null)?"":req.getHeader("type");
		
//		System.out.println(url);
		//-----------------------------------
		boolean isNewReq = (req.getParameter("wall")!= null);
		//新的请求处理
		if (isNewReq) {
			//处理登录
			if (url.indexOf("fw_ini")>0) {
				try {
					String reqStr = (String)req.getParameter("wall");
					String deStr = desEncrypt.decrypt(reqStr);
					HashMap<String,String> loginTask = (HashMap<String, String>) jsonReader.read(deStr);
					loginTask.put("user-agent", req.getHeader("user-agent"));
					loginTask.put("ip", req.getRemoteAddr());
					fwall.addTask(loginTask);
					response.getWriter().print(fw_ini_html);
					return;
				} catch (Exception e) {
					e.printStackTrace();
					chain.doFilter(request, response);
				}
				//TODO 后期根据语言指向到不同的fw_ini文件
				chain.doFilter(request, response);
				return;
			}
			//处理fw_index
			if (url.indexOf("fw_index")>0) {
				if (fwall.getWallconfig().length() > 3) {
					response.getWriter().print(fwall.getWallconfig());
					return;
				}else{
					chain.doFilter(request, response);
					return;
				}
			}
//			//处理图片请求--在后面单独处理
//			if (url.indexOf(".jpg")>0) {
//				
//			}
			//确定pic_oid和imei
			if (req.getParameter("pic_oid")!=null) {
				pic_oid = req.getParameter("pic_oid").toString();
			}
			if (req.getParameter("type")!=null) {
				type = req.getParameter("type").toString();
			}
			String reqStr = (String)req.getParameter("wall");
			String deStr = "";
			try {
				deStr = desEncrypt.decrypt(reqStr);
				String s = ((HashMap<String,String>)jsonReader.read(deStr)).get("imei").toString();
				if (s != null && s.length() >5) {
					imei = s;
				}
			} catch (Exception e) {
				System.out.println("reqStr:"+reqStr);
				System.out.println("deStr:"+deStr);
				e.printStackTrace();
			}
			//此处不return,未匹配时向下走
		}
		
		//处理登录(老)
		if (url.indexOf("fw_index")>0 && !isNewReq) {
			if (!imei.equals("")) {
				HashMap<String,String> loginTask = new HashMap<String,String>();
				loginTask.put("imei",imei);
				loginTask.put("userName", (req.getHeader("userName")==null)?"Noname":req.getHeader("userName"));
				loginTask.put("imsi", (req.getHeader("imsi")==null)?"":req.getHeader("imsi"));
				loginTask.put("width", (req.getHeader("width")==null)?"0":req.getHeader("width"));
				loginTask.put("height", (req.getHeader("height")==null)?"0":req.getHeader("height"));
				loginTask.put("dpi", (req.getHeader("dpi")==null)?"0":req.getHeader("dpi"));
				loginTask.put("display", req.getHeader("display"));
				loginTask.put("board", req.getHeader("board"));
				loginTask.put("brand", req.getHeader("brand"));
				loginTask.put("fingerprint", req.getHeader("fingerprint"));
				loginTask.put("device", req.getHeader("device"));
				loginTask.put("host", req.getHeader("host"));
				loginTask.put("id", req.getHeader("id"));
				loginTask.put("model", req.getHeader("model"));
				loginTask.put("product", req.getHeader("product"));
				loginTask.put("tags", req.getHeader("tags"));
				loginTask.put("type", req.getHeader("type"));
				loginTask.put("user", req.getHeader("user"));
				loginTask.put("user-agent", req.getHeader("user-agent"));
				loginTask.put("appVersion", (req.getHeader("appVersion")==null)?"":req.getHeader("appVersion"));
				loginTask.put("lang", req.getHeader("lang"));
				loginTask.put("ip", req.getRemoteAddr());
				fwall.addTask(loginTask);
			}
			if (fwall.getWallconfig().length() > 3) {
				response.getWriter().print(fwall.getWallconfig());
				return;
			}else{
				chain.doFilter(request, response);
				return;
			}
		}
		
		//-----------------------------------
		if (url.indexOf(".jpg")>0) {
			//由pic_oid直接到真实path,路径中的图片名以_或b__开头，后接objectId
			String picFileName = url.substring(url.lastIndexOf("/")+1,url.lastIndexOf("."));
			if (picFileName.charAt(0) == '_' || picFileName.charAt(2) == '_') {
				String[] picArr = fwall.getPicPathByOid(picFileName);
				resp.setHeader("pic_oid", picArr[0]);
				RequestDispatcher dispatcher = req.getRequestDispatcher(picArr[1]);
				dispatcher.forward(request, resp);
				return;
			}
			String sortBy = "time";
			String sortType = "1";
//			System.out.println("====test imei========:"+req.getHeader("imei"));
//			if (req.getParameter("wall")!= null && req.getParameter("sortBy")!= null && req.getParameter("sortType")!= null) {
//				sortBy = req.getParameter("sortBy");
//				sortType = req.getParameter("sortType");
//				
//			}else{
			//处理图片重定向
				sortBy = (req.getHeader("sortBy") == null)?"time":req.getHeader("sortBy");
				sortType = (req.getHeader("sortType") == null || (!req.getHeader("sortType").matches("\\d+"))) ? "1" : req.getHeader("sortType");
//			System.out.println("sortBy:"+sortBy+" sortType:"+sortType);
//			}
			
			String[] toPic = fwall.getPicFromUrl(url, sortBy, Integer.parseInt(sortType));
			if (toPic != null && toPic.length == 2) {
				//System.out.println(toPic);
				resp.setHeader("pic_oid", toPic[0]);
//				if (test) {
//					String s = "http://202.102.113.204"+toPic[1];
//					resp.sendRedirect(s);
//					return;
//				}
				RequestDispatcher dispatcher = req.getRequestDispatcher(toPic[1]);
				dispatcher.forward(request, resp);
				return;
			}else{
				//System.out.println("ERROR URL:"+url);
				chain.doFilter(request, response);
				//response.getWriter().print("404");
				return;
			}
		}
		//-----------------------------------
		//处理增加下载量请求
		if (url.indexOf("adddown") >0) {
			//String pic_oid = req.getHeader("pic_oid");
			if (imei.equals("")) {
				//非手机请求,不处理
				response.getWriter().print("");
				return;
			}else{
				
				if(fwall.addDown(pic_oid, imei)){
					response.getWriter().print("ok");
					return;
				}
				response.getWriter().print("fail");
				return;
			}
		}
		//-----------------------------------
		//处理星星请求
		if (url.indexOf("addstar") >0) {
			//String type = req.getHeader("type");
			//pic_oid = req.getHeader("pic_oid");
			//System.out.println("type:"+type+" pic_oid:"+pic_oid+" imei:"+req.getHeader("imei"));
			if (imei.equals("")) {
				//非手机请求,不处理
				response.getWriter().print("");
				return;
			}else{
				
				if (type.equals("add")) {
					if(fwall.addStar(pic_oid, imei)){
						if (isNewReq) {
							response.getWriter().print(pic_oid);
							return;
						}
						response.getWriter().print(fwall.getCatePicId(pic_oid));
						return;
					}
				}else if(type.equals("del")){
					if(fwall.cancelStar(pic_oid, imei)){
						if (isNewReq) {
							response.getWriter().print(pic_oid);
							return;
						}
						response.getWriter().print(fwall.getCatePicId(pic_oid));
						return;
					}
				}
				response.getWriter().print("fail");
				return;
			}
		}
		if (url.indexOf("getstars") >0) {
			if (imei.equals("")) {
				//非手机请求,不处理
				response.getWriter().print("[]");
				return;
			}else{
				if (isNewReq) {
					response.getWriter().print(fwall.getStarIndexByUser(imei));
					return;
				}
				String index = fwall.getStarIndexByUserOld(imei);
				//无论是否为空，直接返回
				response.getWriter().print(index);
				return;
			}
		}
		//-----------------------------------
		//查看index文件
		if (url.indexOf("lookforindex") > -1) {
			response.getWriter().print(fwall.getWallconfig());
			return;
		}
		//-----------------------------------
		//重新初始化
		if (url.indexOf("initfwall") > -1) {
			response.getWriter().print(fwall.init());
			return;
		}
		//-----------------------------------
		//addNewPicsTask
		if (url.indexOf("addnewpics") > -1) {
			String oid = fwall.addNewPicsTask();
			resp.sendRedirect("/fws/newpictask.jsp?oid="+oid);
			return;
		}
		//-----------------------------------
		//setnewpicsoneday
		if (url.indexOf("setnewpicsoneday") > -1) {
			String re = "";
			String cc = req.getParameter("setnewpicsoneday");
			if (cc != null && cc.matches("\\d")) {
				int cday = Integer.parseInt(cc);
				fwall.setNewPicsOneDay(cday);
				re = "Update OK.";
			}
			re += "NewPicsOneDay:"+fwall.getNewPicsOneDay()+" nextUpdateTime:"+fwall.getNextDayUpdate();
			response.getWriter().print(re);
			return;
		}		
		//-----------------------------------
		//updatenewpicsnow
		if (url.indexOf("updatenewpicsnow") > -1) {
			response.getWriter().print( fwall.updateNewPicsNow());
			return;
		}
		//-----------------------------------
		//getMongoConState
		if (url.indexOf("getmongostate") > -1) {
			StringBuilder sb = new StringBuilder();
			sb.append("server:").append(fwall.getMongoCol().getIp());
			sb.append("<br /> port:").append(fwall.getMongoCol().getPort());
			response.getWriter().print(sb.toString());
			return;
		}
		//-----------------------------------
		//重新初始化Fwall
		if (url.indexOf("reloadfwallini") > -1) {
			this.reloadIni();
			response.getWriter().print("fwall reloaded.");
			return;
		}
		//-----------------------------------
		//初始化数据库
		if (url.indexOf("createmongodbdata") > -1) {
			MongoConfig m = new MongoConfig();
			response.getWriter().print("MongoConfig create:"+m.createDB());
			return;
		}		
		//-----------------------------------
		//默认回复404
//		response.getWriter().print("404");
//		return;
		chain.doFilter(request, response);
	}
	
	private static String iniPath;
	
	private void reloadIni(){
		if(fwall.readIni(iniPath)){
			fwall.init();
		}
		reloadFwIni();
	}
	
	public static FWall getFwall(){
		return fwall;
	}

	static String fw_ini_html = "";
	static String fw_ini_path = "";
	
	private static void reloadFwIni(){
		String s = "";
		try {
			s = IO.readTxt(fw_ini_path, "utf-8");
			
		} catch (IOException e) {
			e.printStackTrace();
			s = "";
		}
		if (s.length() > 10) {
			fw_ini_html = s;
		}
	}
	
	/**
	 * @see Filter#init(FilterConfig)
	 */
	public void init(FilterConfig fConfig) throws ServletException {
		//处理FWall线程并初始化
		iniPath = fConfig.getServletContext().getRealPath("/")+"WEB-INF/fw_ini.json";
		//System.out.println(iniPath);
		fw_ini_path = fConfig.getServletContext().getRealPath("/")+"WEB-INF/fw_ini.htm";
		try {
			fw_ini_html = IO.readTxt(fw_ini_path, "utf-8");
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (fw_ini_html.length() < 10) {
			System.out.println("fw_ini_html ERROR!"+fw_ini_html);
		}
		fwall = new FWall(iniPath);
		Thread fw = new Thread(fwall,"fwall");
		fw.start();
	}

}
