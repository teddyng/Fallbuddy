package teddyng.fallbuddy;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.builder.filter.Comparison;
import com.mbientlab.metawear.builder.filter.ThresholdOutput;
import com.mbientlab.metawear.builder.function.Function1;
import com.mbientlab.metawear.module.Accelerometer;

import bolts.Continuation;
import bolts.Task;

public class scanner extends AppCompatActivity implements ServiceConnection {

    private BtleService.LocalBinder serviceBinder;
    private MetaWearBoard board;
    private Accelerometer accelerometer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        // Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);

        findViewById(R.id.startbtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accelerometer.acceleration().start();
                accelerometer.start();
            }
        });
        findViewById(R.id.stopbtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accelerometer.stop();
                accelerometer.acceleration().stop();
            }
        });
        findViewById(R.id.resetbtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                board.tearDown();
            }
        });

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // Typecast the binder to the service's LocalBinder class
        serviceBinder = (BtleService.LocalBinder) service;

        retrieveBoard("F2:EE:BA:A8:DD:5F");
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    public void retrieveBoard(String MW_MAC_ADDRESS) {
        final BluetoothManager btManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice =
                btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS);

        // Create a MetaWear board object for the Bluetooth Device
        board = serviceBinder.getMetaWearBoard(remoteDevice);
        board.connectAsync().onSuccessTask(new Continuation<Void, Task<Route>>() {
            @Override
            public Task<Route> then(Task<Void> task) throws Exception {


                accelerometer= board.getModule(Accelerometer.class);
                accelerometer.configure()
                        //.odr(25f)       // Set sampling frequency to 25Hz, or closest valid ODR
                        .odr(60f)       // Set sampling frequency to 50Hz, or closest valid ODR
                        .commit();
                return accelerometer.acceleration().addRouteAsync(new RouteBuilder() {
                    @Override
                    public void configure(RouteComponent source) {
                        source.map(Function1.RSS).average((byte)4).filter(ThresholdOutput.BINARY, 0.5f)
                                .multicast()
                                .to().filter(Comparison.EQ, -1).stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                Log.i("in freefall", "in freefall");

                            }
                        })
                                .to().filter(Comparison.EQ,-1).stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                Log.i("in freefall", "not freefall");
                            }
                        }).end();
//                        source.stream(new Subscriber() {
//                            @Override
//                            public void apply(Data data, Object... env) {
//                                Log.i("freefall", data.value(Acceleration.class).toString());
//                            }
//                        });
                    }
                });
            }
        }).continueWith(new Continuation<Route, Void>() {
            @Override
            public Void then(Task<Route> task) throws Exception {
//                accelerometer.acceleration().start();
//                accelerometer.start();
                return null;
            }
        });
    }
}
