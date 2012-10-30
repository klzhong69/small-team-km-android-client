package com.teamkn.activity.dataitem.pull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Inflater;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.Transformation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.teamkn.R;
import com.teamkn.Logic.HttpApi;
import com.teamkn.base.activity.TeamknBaseActivity;
import com.teamkn.base.task.TeamknAsyncTask;
import com.teamkn.base.utils.BaseUtils;
import com.teamkn.model.DataItem;
import com.teamkn.model.DataList;
import com.teamkn.model.database.DataItemDBHelper;
import com.teamkn.model.database.DataListDBHelper;
import com.teamkn.pinyin4j.PinyinComparator;
import com.teamkn.widget.adapter.DataItemTermWiseUpdateAdapter;

public class DataItemTermWiseUpdateActivity extends TeamknBaseActivity{
	public static class RequestCode {
		public final static String CREATE = "CREATE";
		public final static String UPDATE = "UPDATE";
		public final static String REMOVE = "REMOVE";
		public final static String ORDER = "ORDER";
		
		public final static String GET = "GET";
		public final static String ACCEPT = "ACCEPT";
		public final static String REJUST = "REJUST";
		
	}
	TextView data_list_title_tv;
	
	ListView list_view;
	Button accept_button;
	ProgressBar progressBar;
	
	String[] seeds;
	DataList dataList_forked;
	DataList dataList_origin;
	
	List<DataItem> dataItems;
	List<DataItem> recordItems;
	
	public static DataItem dataItem;
	int committer_id;
	
	int count_forked;
	boolean is_first = true;
	public static DataItemTermWiseUpdateAdapter adapter;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.data_item_termwise_update_list);
		Intent intent = getIntent();
		Integer dataList_forked_id = intent.getIntExtra("server_dataList_forked_id", -1);
		Integer dataList_origin_id = intent.getIntExtra("server_dataList_origin_id", -1);
		seeds = intent.getStringArrayExtra("seeds");
		dataList_forked = DataListDBHelper.find_by_server_data_list_id(dataList_forked_id);
		dataList_origin = DataListDBHelper.find_by_server_data_list_id(dataList_origin_id);
		
		committer_id = intent.getIntExtra("committer_id", -1);
		is_first = true;
		load_UI();
	}
	private void load_UI() {
		data_list_title_tv = (TextView)findViewById(R.id.data_list_title_tv);
		data_list_title_tv.setText(dataList_origin.title);
		list_view = (ListView)findViewById(R.id.list_view);
		api_load_list(RequestCode.GET);
		accept_button = (Button)findViewById(R.id.accept_button);
		progressBar = (ProgressBar)findViewById(R.id.progressBar);
		progressBar.setProgress(0);
	}
	private void api_load_list(final String action){
		if(BaseUtils.is_wifi_active(this)){
			new  TeamknAsyncTask<Void, Void, DataItem>(DataItemTermWiseUpdateActivity.this,"正在加载中") {
				@Override
				public DataItem do_in_background(Void... params) throws Exception {
					System.out.println("dataList_origin = "+dataList_origin.toString());
					System.out.println("dataList_forked = "+dataList_forked.toString());
					if(action.equals(RequestCode.GET)){
						dataItem = HttpApi.WatchList.next_commits(dataList_origin.server_data_list_id, committer_id);
					}else if(action.equals(RequestCode.ACCEPT)){
						dataItem = HttpApi.WatchList.accept_next_commit(dataList_origin.server_data_list_id, committer_id,dataItem);
					}else if(action.equals(RequestCode.REJUST)){
						dataItem = HttpApi.WatchList.reject_next_commit(dataList_origin.server_data_list_id, committer_id);
					}
					dataItems = DataItemDBHelper.all(dataList_origin.id);
//					dataItems.add(dataItem.getNext_commits_count(), dataItem);	
					return dataItem;
				}
				@Override
				public void on_success(DataItem result) {
					set_progressBar(result.getNext_commits_count());
					if(result.getTitle()!=null && result.getTitle()!="" && !result.isConflict()){
						accept_button.setClickable(true);
						updateOrInsert_dataItem(result);
						load_list_view();
					}else if( result.isConflict()){
						accept_button.setClickable(false);
						accept_button.setBackgroundColor(getResources().getColor(R.color.antiquewhite));
						load_list_view();
					}
				}
			}.execute();
		}else{
			BaseUtils.toast("无法连接网络");
		}
	}
	private List<DataItem> updateOrInsert_dataItem(DataItem data_Item){
		recordItems = dataItems;
		//创建
		if(data_Item.getOperation().equals(RequestCode.CREATE)){
			System.out.println(" create " + data_Item.toString());
			dataItems.add(data_Item);	
		//移动
		}else if(data_Item.getOperation().equals(RequestCode.ORDER)){
			System.out.println(" order " + data_Item.toString());
			for (int i = 0; i < dataItems.size(); i++) {
				System.out.println("item : " + dataItems.get(i).toString());
			}
			dataItems = getDataItem(dataItems,data_Item);
		}else{
			List<DataItem> new_dataItems = new ArrayList<DataItem>();
			// 修改  删除
			for(int i = 0 ; i < dataItems.size() ; i++){
				if(data_Item.seed.equals(dataItems.get(i).seed)){
					System.out.println(" update or remove " + data_Item.toString());
					if(data_Item.getOperation().equals(RequestCode.REMOVE)){
						DataItem dataItem2 = dataItems.get(i);
						dataItem2.setOperation(RequestCode.REMOVE);
						new_dataItems.add(dataItem2);
					}else if(data_Item.getOperation().equals(RequestCode.UPDATE)){
						new_dataItems.add(data_Item);
					}	
				}else{
					new_dataItems.add(dataItems.get(i));
				}
			}
			dataItems = new_dataItems;
		}
		return dataItems;
	}
	
	private void system(String[] names){
		for(String item : names){
			System.out.print(item + " : ");
		}
		System.out.println("order");
	}
	
	private List<DataItem> getDataItem(List<DataItem> new_dataItems ,DataItem data_Item){
		
		List<DataItem> new_item = new ArrayList<DataItem>();
		boolean position_equal = false;
		boolean equal_fully = false;
		for(DataItem item : new_dataItems){
			if(item.position.equals(data_Item.position)){
				position_equal = true;
				if(item.seed.equals(data_Item.seed)){
					new_item.add(data_Item);
					equal_fully = true;
				}else{
					new_item.add(item);
					new_item.add(data_Item);
				}
			}else{
				new_item.add(item);
			}
		}
		if(equal_fully){
			accept_button.setClickable(false);
			accept_button.setBackgroundColor(getResources().getColor(R.color.antiquewhite));	
		}
		
		if(position_equal == false){
			List<DataItem> new_item_order = new ArrayList<DataItem>();
			dataItems.add(data_Item);
			String[] names = new String[dataItems.size()];
			for(int i = 0 ;i<dataItems.size();i++){
				names[i] = dataItems.get(i).position;
			}
			system(names);
			//排序(实现了中英文混排)
//			Arrays.sort(names, new PinyinComparator());
			for(int i=names.length-1; i>=0; i--) {
				for(int j=0; j<i; j++) {
				    if(names[j].compareTo(names[j+1]) < 0) {
				    	String temp = names[j];
				    	names[j] = names[j+1];
				    	names[j+1] = temp;
				    }
				}
			}
			system(names);
			
			for(int i = 0 ; i<dataItems.size() ; i++){
				for(int j = 0;j<names.length ; j ++){
					if(dataItems.get(i).position.equals(names[j])){
						boolean has = false;
						for(DataItem item : new_item_order){
							if(item.toString().equals(dataItems.get(i).toString())){
								has = true;
							}
						}
						if(!has){
							new_item_order.add(dataItems.get(i));
						}	
					}
				}
			}
			new_item = new_item_order;
			System.out.println(new_item.size());
		}	
		return new_item;
	}
	
	private void load_list_view() {	
		try {
//			recordItems;
			if((dataItem.getOperation().equals(RequestCode.UPDATE)) 
					&& dataItem.isConflict()!=true){
				AlphaAnimation alphaAnimation = new AlphaAnimation(1.0f, 1.1f);
		    	alphaAnimation.setDuration(2000);
		    	alphaAnimation.setFillEnabled(true);
//		    	alphaAnimation.setRepeatCount(3000);
//		    	alphaAnimation.setFillBefore(true);
		    	list_view.startAnimation(alphaAnimation);
		    	alphaAnimation.setAnimationListener(new AnimationListener() {
					@Override
					public void onAnimationStart(Animation animation) {	
						adapter = new DataItemTermWiseUpdateAdapter(DataItemTermWiseUpdateActivity.this);
						adapter.add_items(recordItems);
						list_view.setAdapter(adapter);
						adapter.notifyDataSetChanged();
					}
					@Override
					public void onAnimationRepeat(Animation animation) {
					}
					@Override
					public void onAnimationEnd(Animation animation) {
						adapter = new DataItemTermWiseUpdateAdapter(DataItemTermWiseUpdateActivity.this);
						adapter.add_items(dataItems);
						list_view.setAdapter(adapter);
						adapter.notifyDataSetChanged();
					}
				});	
			}else{
				adapter = new DataItemTermWiseUpdateAdapter(DataItemTermWiseUpdateActivity.this);
				adapter.add_items(dataItems);
				list_view.setAdapter(adapter);
				adapter.notifyDataSetChanged();
			}
//			adapter.
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public void set_progressBar(int next_commits_count){
		if(is_first){
			count_forked = next_commits_count;
			is_first = false;
			progressBar.setMax(next_commits_count);
		}
		progressBar.setProgress(count_forked-next_commits_count);
		if(next_commits_count==0){
//			this.finish();
			Intent intent = new Intent(DataItemTermWiseUpdateActivity.this,DataItemPullListActivity.class);
			intent.putExtra("data_list_id", dataList_origin.id);
			intent.putExtra("data_list_public", "fork");
			startActivity(intent);
		}
	}
	public void click_accept_button(View view){
		api_load_list(RequestCode.ACCEPT);
	}
	public void click_refuse_button(View view){
		api_load_list(RequestCode.REJUST);
	}
}
