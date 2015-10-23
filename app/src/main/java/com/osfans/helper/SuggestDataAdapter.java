package com.osfans.helper;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.osfans.trime.R;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by rmss on 2015/10/23.
 */
public class SuggestDataAdapter extends BaseAdapter{
    List<String> dataSet;
    Context mContext;
    private static final String URL_PREFIX="http://m.chinaso.com/page/search.htm?keys=";


    public SuggestDataAdapter(Context context,List<String> ds){
        mContext=context;
        dataSet=ds;
    }

    @Override
    public int getCount() {
        return dataSet.size();
    }

    @Override
    public Object getItem(int position) {
        return dataSet.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if(convertView==null){
            convertView=LayoutInflater.from(mContext).inflate(R.layout.suggest_data_item,null);

            ViewHolder holder=new ViewHolder();
            holder.suggest_tv= (TextView) convertView.findViewById(R.id.suggest_tv);

            convertView.setTag(holder);

        }
        ViewHolder holder= (ViewHolder) convertView.getTag();
        holder.suggest_tv.setText(dataSet.get(position));
        holder.suggest_tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url=URL_PREFIX+ encodeURL(dataSet.get(position));
                Intent browserIntent= new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                browserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(browserIntent);
            }
        });
        return convertView;
    }
    private String encodeURL(String key){
        String decodeKey = key;
        String encodeKey = null;
        try {
            encodeKey = URLEncoder.encode(decodeKey, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return encodeKey;
    }
    class ViewHolder{
        TextView suggest_tv;
    }
}
