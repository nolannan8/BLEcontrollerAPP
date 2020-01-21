package com.avnan.blecontrollerapp;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.LinkedList;

public class ScanListAdapter extends RecyclerView.Adapter<ScanListAdapter.DeviceViewHolder>{
    public static final String EXTRA_DEVICE_NAME = "com.avnan.blerecyclerview.extra.DEVICE_NAME";
    public static final String EXTRA_DEVICE_ADDRESS = "com.avnan.blerecyclerview.extra.DEVICE_ADDRESS";
    public static final String EXTRA_DEVICE = "com.avnan.blecontrollerapp.extra.DEVICE";

    class DeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        public final TextView deviceNameView;
        public final TextView deviceAddressView;
        final ScanListAdapter mAdapter;

        public DeviceViewHolder(View itemView, ScanListAdapter adapter) {
            super(itemView);
            deviceNameView = itemView.findViewById(R.id.deviceName);
            deviceAddressView = itemView.findViewById(R.id.deviceAddress);
            this.mAdapter = adapter;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int mPosition = getLayoutPosition();
            // Access the affected device
            BluetoothDevice device = mDeviceList.get(mPosition);
            Log.d("Adapter", "Clicked on " + device.getName());
            Intent intent = new Intent(view.getContext(), DeviceDetailsActivity.class);
            intent.putExtra(EXTRA_DEVICE, device);
            view.getContext().startActivity(intent);
        }
    }

    private final LinkedList<BluetoothDevice> mDeviceList;
    private LayoutInflater mInflater;

    public ScanListAdapter(Context context, LinkedList<BluetoothDevice> deviceList) {
        mInflater = LayoutInflater.from(context);
        this.mDeviceList = deviceList;
    }

    @Override
    public ScanListAdapter.DeviceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View mItemView = mInflater.inflate(R.layout.devicelist_item, parent, false);
        return new DeviceViewHolder(mItemView, this);
    }

    @Override
    public void onBindViewHolder(ScanListAdapter.DeviceViewHolder holder, int position) {
        BluetoothDevice mCurrent = mDeviceList.get(position);
        holder.deviceNameView.setText(mCurrent.getName());
        holder.deviceAddressView.setText(mCurrent.getAddress());
    }

    @Override
    public int getItemCount() {
        return mDeviceList.size();
    }
}
