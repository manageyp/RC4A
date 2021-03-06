/*Copyright (C) 2012 Longerian (http://www.longerian.me)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/
package org.rubychina.android.activity;

import greendroid.app.GDActivity;
import greendroid.widget.ActionBarItem;
import greendroid.widget.ActionBarItem.Type;
import greendroid.widget.LoaderActionBarItem;

import java.lang.ref.Reference;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.rubychina.android.R;
import org.rubychina.android.RCApplication;
import org.rubychina.android.RCService;
import org.rubychina.android.RCService.LocalBinder;
import org.rubychina.android.api.request.TopicDetailRequest;
import org.rubychina.android.api.response.TopicDetailResponse;
import org.rubychina.android.type.Reply;
import org.rubychina.android.type.Topic;
import org.rubychina.android.type.User;
import org.rubychina.android.util.ImageParser;

import yek.api.ApiCallback;
import yek.api.ApiException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

public class TopicDetailActivity extends GDActivity {

	public static final String POS = "org.rubychina.android.activity.TopicDetailActivity.POSITION";
	public static final String TOPIC = "org.rubychina.android.activity.TopicDetailActivity.TOPIC";
	private static final String TAG = "TopicDetailActivity";
	
	private LoaderActionBarItem progress;
	private ImageView gravatar;
	private ListView replies;
	private ImageParser ip;

	private RCService mService;
	private boolean isBound = false; 
	private Topic t;
	
	private TopicDetailRequest request;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setActionBarContentView(R.layout.topic_layout);
		progress = (LoaderActionBarItem) addActionBarItem(Type.Refresh, R.id.action_bar_refresh);
		
		Gson g = new Gson(); 
		t = g.fromJson(getIntent().getStringExtra(TOPIC), Topic.class);
		
		ip = new ImageParser(getApplicationContext());
	}
	
	@Override
	protected void onStart() {
		super.onStart();
        Intent intent = new Intent(this, RCService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}
	
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            isBound = true;
            startTopicDetailRequest(t.getId());
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };
    
    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(mConnection);
            isBound = false;
        }
    }
	
	@Override
	public boolean onHandleActionBarItemClick(ActionBarItem item, int position) {
		switch (item.getItemId()) {
        case R.id.action_bar_replies:
        	Intent i = new Intent(getApplicationContext(), ReplyListActivity.class);
        	i.putExtra(ReplyListActivity.BELONG_TO_TOPIC, t.getId());
        	startActivity(i);
        	return true;
        case R.id.action_bar_refresh:
        	startTopicDetailRequest(t.getId());
        	return true;
        default:
            return super.onHandleActionBarItemClick(item, position);
		}
	}
	
	private void startTopicDetailRequest(int id) {
		if(request == null) {
			request = new TopicDetailRequest(id);
		}
		request.setId(id);
		((RCApplication) getApplication()).getAPIClient().request(request, new TopicDetailCallback());
		progress.setLoading(true);
	}
	
	private class TopicDetailCallback implements ApiCallback<TopicDetailResponse> {

		@Override
		public void onException(ApiException e) {
			Toast.makeText(getApplicationContext(), R.string.hint_loading_data_failed, Toast.LENGTH_SHORT).show();
			progress.setLoading(false);
		}

		@Override
		public void onFail(TopicDetailResponse r) {
			Toast.makeText(getApplicationContext(), R.string.hint_loading_data_failed, Toast.LENGTH_SHORT).show();
			progress.setLoading(false);
		}

		@Override
		public void onSuccess(TopicDetailResponse r) {
			progress.setLoading(false);
			refreshView(r.getReplies());
		}
		
	}
	
	private class ReplyAdapter extends ArrayAdapter<Reply> {

		private List<Reply> items;
		private Context context;
		private int resource;
		
		public ReplyAdapter(Context context, int resource,
				int textViewResourceId, List<Reply> items) {
			super(context, resource, textViewResourceId, items);
			this.context = context;
			this.resource = resource;
			this.items = items;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final ViewHolder viewHolder;
			if(convertView == null) {
				viewHolder = new ViewHolder();
				convertView = LayoutInflater.from(context).inflate(resource, null);
				viewHolder.gravatar = (ImageView) convertView.findViewById(R.id.gravatar);
				viewHolder.userName = (TextView) convertView.findViewById(R.id.user_name);
				viewHolder.floor = (TextView) convertView.findViewById(R.id.floor);
				viewHolder.body = (TextView) convertView.findViewById(R.id.body);
				viewHolder.forward = (ImageView) convertView.findViewById(R.id.forward);
				convertView.setTag(viewHolder);
			} else {
				viewHolder = (ViewHolder) convertView.getTag();
			}
			final Reply r = items.get(position);
			mService.requestUserAvatar(r.getUser(), viewHolder.gravatar, 0);
			viewHolder.userName.setText(r.getUser().getLogin());
			viewHolder.floor.setText(position + 1 + "" + getString(R.string.reply_list_unit));
//			new RetrieveSpannedTask(viewHolder.body).execute(r.getBodyHTML());
			viewHolder.body.setText(Html.fromHtml(r.getBodyHTML()));
			viewHolder.gravatar.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					visitUserProfile(r.getUser());
				}
			});
			return convertView;
		}
		
		private class ViewHolder {
			
			public ImageView gravatar;
			public TextView userName;
			public TextView floor;
			public TextView body;
			public ImageView forward;
			
		}
		
	}
	
	private void refreshView(List<Reply> rs) {
		if(replies == null) {
			replies = (ListView) findViewById(R.id.replies);
			replies.addHeaderView(initializeTopicBody(), null, false);
		}
		Collections.sort(rs);
		replies.setAdapter(new ReplyAdapter(getApplicationContext(), R.layout.reply_item,
				R.id.body, rs));
	}
	
	private View initializeTopicBody() {
		View body = LayoutInflater.from(getApplicationContext()).inflate(R.layout.topic_body_layout, null);
		
		gravatar = (ImageView) body.findViewById(R.id.gravatar);
		gravatar.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				visitUserProfile(t.getUser());
			}
		});
		requestUserAvatar();
		
		TextView title = (TextView) body.findViewById(R.id.title);
		title.setText(t.getTitle());
		
		TextView bodyText = (TextView) body.findViewById(R.id.body);

		new RetrieveSpannedTask(bodyText).execute(t.getBodyHTML());
		return body;
	}
	
	private void visitUserProfile(User u) {
		Gson g = new Gson();
		Intent i = new Intent(getApplicationContext(), UserProfileActivity.class);
		i.putExtra(UserProfileActivity.VIEW_PROFILE, g.toJson(u));
		startActivity(i);
	}
	
	private void requestUserAvatar() {
		if(gravatar == null) {
			gravatar = (ImageView) findViewById(R.id.gravatar);
		}
		mService.requestUserAvatar(t.getUser(), gravatar, 0);
	}
	
	private class RetrieveSpannedTask extends AsyncTask<String, Void, Spanned> {

		private TextView htmlView;
		
		public RetrieveSpannedTask(TextView htmlView) {
			this.htmlView = htmlView;
		}
		
		@Override
		protected void onPreExecute() {
			progress.setLoading(true);
		}

		@Override
		protected Spanned doInBackground(String... params) {
			return Html.fromHtml(params[0], mService.getImageGetter(), null);
		}
		
		@Override
		protected void onPostExecute(Spanned result) {
			progress.setLoading(false);
			htmlView.setText(result);
		}
		
	}
	
}
