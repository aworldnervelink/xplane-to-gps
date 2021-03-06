package com.appropel.xplanegps.view.fragment;

import android.Manifest;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v13.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.appropel.xplane.udp.UdpUtil;
import com.appropel.xplanegps.R;
import com.appropel.xplanegps.dagger.DaggerWrapper;
import com.appropel.xplanegps.model.Preferences;
import com.appropel.xplanegps.view.util.IntentProvider;
import com.appropel.xplanegps.view.util.LocationUtilImpl;
import com.appropel.xplanegps.view.util.SettingsUtil;
import com.appropel.xplanegps.view.util.ViewUtil;

import java.text.DateFormat;
import java.util.Date;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import de.greenrobot.event.EventBus;

/**
 * Activity which displays the data stream coming from X-Plane.
 */
public final class DataFragment extends Fragment    // NOPMD
{
    /** Key for shared pref. */
    public static final String PREF_VALUE = "data";

    /** Package name for Copilot X. */
    private static final String COPILOT_X = "com.appropel.xplanevoice";

    /** Time format. */
    private final DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.LONG);

    /** View holding latitude. */
    @BindView(R.id.latitude_view)
    TextView latitudeView;

    /** View holding longitude. */
    @BindView(R.id.longitude_view)
    TextView longitudeView;

    /** View holding altitude. */
    @BindView(R.id.altitude_view)
    TextView altitudeView;

    /** View holding heading. */
    @BindView(R.id.heading_view)
    TextView headingView;

    /** View holding groundspeed. */
    @BindView(R.id.groundspeed_view)
    TextView groundspeedView;

    /** View holding fix time. */
    @BindView(R.id.time_view)
    TextView timeView;

    /** Button to activate service. */
    @BindView(R.id.active_button)
    CompoundButton activeButton;

    /** Banner ad. */
    @BindView(R.id.copilot_x_ad)
    View bannerAd;

    /** On switch. */
    @BindView(R.id.on_switch)
    View onSwitch;

    /** Data table. */
    @BindView(R.id.table_layout)
    View dataTable;

    /** Intent provider. */
    @Inject
    IntentProvider intentProvider;

    /** Preferences. */
    @Inject
    Preferences preferences;

    /** Event bus. */
    @Inject
    EventBus eventBus;

    /** UDP utilities. */
    @Inject
    UdpUtil udpUtil;

    /** Used by ButterKnife. */
    private Unbinder unbinder;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
    {
        final View view = inflater.inflate(R.layout.data, container, false);
        unbinder = ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) // NOPMD
    {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        DaggerWrapper.INSTANCE.getDaggerComponent().inject(this);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);
        final Intent dataServiceIntent = intentProvider.getServiceIntent();
        activeButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            public void onCheckedChanged(final CompoundButton compoundButton, final boolean isChecked)
            {
                if (activeButton.isChecked())
                {
                    getActivity().startService(dataServiceIntent);
                }
                else
                {
                    getActivity().stopService(dataServiceIntent);
                }
            }
        });

        // Hide the banner ad if it would obscure the screen.
        ViewTreeObserver vto = bannerAd.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener()
        {
            @Override
            public void onGlobalLayout()
            {
                bannerAd.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                if (ViewUtil.intersects(bannerAd, onSwitch) || ViewUtil.intersects(bannerAd, dataTable))
                {
                    bannerAd.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void onStart()
    {
        super.onStart();
        requestPermissions();
        activeButton.setEnabled(SettingsUtil.isMockLocationEnabled(getActivity()));
        activeButton.setChecked(udpUtil.isReceiverRunning());
        eventBus.register(this);
    }


    /**
     * Handler for when the user clicks on the Copilot X advertisement.
     */
    @OnClick(R.id.copilot_x_button)
    public void onClickCopilotX()
    {
        try
        {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + COPILOT_X)));
        }
        catch (android.content.ActivityNotFoundException anfe)
        {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + COPILOT_X)));
        }
    }

    @Override
    public void onStop() // NOPMD
    {
        eventBus.unregister(this);
        super.onStop();
    }

    @Override
    public void onDestroyView()
    {
        unbinder.unbind();
        super.onDestroyView();
    }

    /**
     * Requests the needed permissions in order to get Android to prompt the user.
     */
    private void requestPermissions()
    {
        if (ContextCompat.checkSelfPermission(
                getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }
    }

    /**
     * Updates the onscreen information from the given location.
     * @param location location.
     */
    public void onEventMainThread(final Location location)
    {
        latitudeView.setText(Location.convert(location.getLatitude(), Location.FORMAT_SECONDS));
        longitudeView.setText(Location.convert(location.getLongitude(), Location.FORMAT_SECONDS));
        altitudeView.setText(String.format("%.0f ft", location.getAltitude() / LocationUtilImpl.FEET_TO_METERS));
        headingView.setText(String.format("%03.0f\u00B0T", location.getBearing()));
        groundspeedView.setText(String.format("%.0f kts", location.getSpeed() / LocationUtilImpl.KNOTS_TO_M_S));
        timeView.setText(timeFormat.format(new Date(location.getTime())));
    }
}
