package org.fox.ttrss;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.fox.ttrss.OnlineServices.RelativeArticle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.ads.AdView;

public class OfflineArticleFragment extends Fragment {
	@SuppressWarnings("unused")
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private int m_articleId;
	private int m_nextArticleId;
	private int m_prevArticleId;

	private Cursor m_cursor;
	private OfflineServices m_offlineServices;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_articleId = savedInstanceState.getInt("articleId");
			m_prevArticleId = savedInstanceState.getInt("prevArticleId");
			m_nextArticleId = savedInstanceState.getInt("nextArticleId");
		}
		
		View view = inflater.inflate(R.layout.article_fragment, container, false);

	
		// TODO change to interface?
		Activity activity = getActivity();
		
		if (activity != null) {		
			int orientation = activity.getWindowManager().getDefaultDisplay().getOrientation();
			
			if (!m_offlineServices.isSmallScreen()) {			
				if (orientation % 2 == 0) {
					view.findViewById(R.id.splitter_horizontal).setVisibility(View.GONE);
				} else {
					view.findViewById(R.id.splitter_vertical).setVisibility(View.GONE);
				}
			} else {
				view.findViewById(R.id.splitter_vertical).setVisibility(View.GONE);
				view.findViewById(R.id.splitter_horizontal).setVisibility(View.GONE);
			}
		} else {
			view.findViewById(R.id.splitter_horizontal).setVisibility(View.GONE);
		}
		
		m_cursor = m_offlineServices.getReadableDb().query("articles", null, BaseColumns._ID + "=?", 
				new String[] { String.valueOf(m_articleId) }, null, null, null);

		m_cursor.moveToFirst();
		
		if (m_cursor.isFirst()) {
			
			TextView title = (TextView)view.findViewById(R.id.title);
			
			if (title != null) {
				
				String titleStr;
				
				if (m_cursor.getString(m_cursor.getColumnIndex("title")).length() > 200)
					titleStr = m_cursor.getString(m_cursor.getColumnIndex("title")).substring(0, 200) + "...";
				else
					titleStr = m_cursor.getString(m_cursor.getColumnIndex("title"));
				
				title.setMovementMethod(LinkMovementMethod.getInstance());
				title.setText(Html.fromHtml("<a href=\""+m_cursor.getString(m_cursor.getColumnIndex("link")).replace("\"", "\\\"")+"\">" + titleStr + "</a>"));
			}
			
			WebView web = (WebView)view.findViewById(R.id.content);
			
			if (web != null) {
				
				String content;
				String cssOverride = "";
				
				
				//WebSettings ws = web.getSettings();
				//ws.setSupportZoom(true);
				//ws.setBuiltInZoomControls(true);

				if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
					cssOverride = "body { background : black; color : #e0e0e0}\n";			
					web.setBackgroundColor(android.R.color.black);
				} else {
					cssOverride = "";
				}
				
				String articleContent = m_cursor.getString(m_cursor.getColumnIndex("content"));
				Document doc = Jsoup.parse(articleContent);
					
				if (doc != null) {
					if (m_prefs.getBoolean("offline_image_cache_enabled", false)) {
						
						Elements images = doc.select("img");
						
						for (Element img : images) {
							String url = img.attr("src");
							
							if (ImageCacheService.isUrlCached(url)) {						
								img.attr("src", "file://" + ImageCacheService.getCacheFileName(url));
							}						
						}
					}
					
					// thanks webview for crashing on <video> tag
					Elements videos = doc.select("video");
					
					for (Element video : videos)
						video.remove();
					
					articleContent = doc.toString();
				}
				
				content = 
					"<html>" +
					"<head>" +
					"<meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\">" +
					//"<meta name=\"viewport\" content=\"target-densitydpi=device-dpi\" />" +
					"<style type=\"text/css\">" +
					cssOverride +
					"img { max-width : 98%; height : auto; }" +
					"body { text-align : justify; }" +
					"</style>" +
					"</head>" +
					"<body>" + articleContent + "</body></html>";
					
				try {
					web.loadDataWithBaseURL(null, content, "text/html", "utf-8", null);
				} catch (RuntimeException e) {					
					e.printStackTrace();
				}
				
			
			}
			
			TextView dv = (TextView)view.findViewById(R.id.date);
			
			if (dv != null) {
				Date d = new Date(m_cursor.getInt(m_cursor.getColumnIndex("updated")) * 1000L);
				SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy, HH:mm");
				dv.setText(df.format(d));
			}
			
			TextView tagv = (TextView)view.findViewById(R.id.tags);
						
			if (tagv != null) {
				String tagsStr = m_cursor.getString(m_cursor.getColumnIndex("tags"));
				tagv.setText(tagsStr);
			}			
			
			AdView av = (AdView)view.findViewById(R.id.ad);
			
			if (av != null) {
				av.setVisibility(View.GONE);
			}
	
		} 
		
		return view;    	
	}

	@Override
	public void onDestroy() {
		super.onDestroy();	
		
		m_cursor.close();
	}
	
	@Override
	public void onSaveInstanceState (Bundle out) {		
		super.onSaveInstanceState(out);
		
		out.putInt("articleId", m_articleId);
		out.putInt("prevArticleId", m_prevArticleId);
		out.putInt("nextArticleId", m_nextArticleId);

	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		
		m_offlineServices = (OfflineServices)activity;
		
		m_articleId = m_offlineServices.getSelectedArticleId();
		
		m_prevArticleId = m_offlineServices.getRelativeArticleId(m_articleId, m_offlineServices.getActiveFeedId(), RelativeArticle.BEFORE);
		m_nextArticleId = m_offlineServices.getRelativeArticleId(m_articleId, m_offlineServices.getActiveFeedId(), RelativeArticle.AFTER);
	}


}
