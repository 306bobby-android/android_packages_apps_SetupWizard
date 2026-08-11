/*
 * SPDX-FileCopyrightText: 2026 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import static com.google.android.setupcompat.util.ResultCodes.RESULT_ACTIVITY_NOT_FOUND;
import static com.google.android.setupcompat.util.ResultCodes.RESULT_SKIP;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;

import androidx.activity.result.ActivityResult;

import org.lineageos.setupwizard.util.SetupWizardUtils;

public class MicroGSetupActivity extends SubBaseActivity {

    public static final String ACTION_MICROG_SETUP =
            "org.microg.installer.updater.SETUP_WIZARD";
    public static final String PACKAGE_MICROG_UPDATER =
            "org.microg.installer.updater";
    public static final String PACKAGE_GMS =
            "com.google.android.gms";
    public static final String PACKAGE_AURORA_SERVICES =
            "com.aurora.services";

    public static final String EXTRA_INCLUDE_AURORA = "include_aurora";
    public static final String EXTRA_INCLUDE_GSF = "include_gsf";
    public static final String EXTRA_INCLUDE_AURORA_SERVICES = "include_aurora_services";

    private static final float DISABLED_ALPHA = 0.4f;

    private CheckBox mIncludeAuroraCheckBox;
    private CheckBox mIncludeGsfCheckBox;
    private CheckBox mIncludeAuroraServicesCheckBox;
    private View mAuroraServicesContainer;
    private View mAdvancedContainer;
    private ImageView mAdvancedChevron;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Any pre-existing com.google.android.gms ends this page before it is shown.
        // Official GApps means microG must not be installed; an existing microG means
        // the installer did not put it there and must not claim it. Either way there is
        // nothing to install, so the page never appears.
        boolean isGmsInstalled = SetupWizardUtils.isPackageInstalled(this, PACKAGE_GMS);
        boolean isInstallerInstalled =
                SetupWizardUtils.isPackageInstalled(this, PACKAGE_MICROG_UPDATER);

        if (isGmsInstalled || !isInstallerInstalled) {
            finishAction(RESULT_SKIP);
            return;
        }

        setNextText(R.string.next);
        setSkipText(R.string.skip);
        getGlifLayout().setDescriptionText(getString(R.string.setup_microg_summary));

        bindOptions();
        // Also covers recreation, where onStartSubactivity is not called again.
        setNextAllowed(true);
    }

    @Override
    protected void onStartSubactivity() {
        setNextAllowed(true);
    }

    private void bindOptions() {
        mIncludeAuroraCheckBox = findViewById(R.id.aurora_checkbox);
        mIncludeGsfCheckBox = findViewById(R.id.gsf_checkbox);
        mIncludeAuroraServicesCheckBox = findViewById(R.id.aurora_services_checkbox);
        mAuroraServicesContainer = findViewById(R.id.aurora_services_container);
        mAdvancedContainer = findViewById(R.id.advanced_container);
        mAdvancedChevron = findViewById(R.id.advanced_chevron);

        View auroraContainer = findViewById(R.id.aurora_container);
        if (auroraContainer != null && mIncludeAuroraCheckBox != null) {
            auroraContainer.setOnClickListener(v -> {
                mIncludeAuroraCheckBox.setChecked(!mIncludeAuroraCheckBox.isChecked());
                updateAuroraServicesEnabled();
            });
        }

        View gsfContainer = findViewById(R.id.gsf_container);
        if (gsfContainer != null && mIncludeGsfCheckBox != null) {
            gsfContainer.setOnClickListener(
                    v -> mIncludeGsfCheckBox.setChecked(!mIncludeGsfCheckBox.isChecked()));
        }

        if (mAuroraServicesContainer != null && mIncludeAuroraServicesCheckBox != null) {
            mAuroraServicesContainer.setOnClickListener(v -> {
                if (!mAuroraServicesContainer.isEnabled()) {
                    return;
                }
                mIncludeAuroraServicesCheckBox.setChecked(
                        !mIncludeAuroraServicesCheckBox.isChecked());
            });
            // The companion is a privileged ROM prebuilt, not a download. Offering it on
            // a build that does not ship it would be a checkbox that cannot do anything.
            if (!isAuroraServicesPresent()) {
                mAuroraServicesContainer.setVisibility(View.GONE);
            }
        }

        View advancedHeader = findViewById(R.id.advanced_header);
        if (advancedHeader != null && mAdvancedContainer != null) {
            advancedHeader.setOnClickListener(v -> toggleAdvanced());
        }

        updateAuroraServicesEnabled();
    }

    /**
     * Aurora Services ships as a ROM prebuilt, and this flow may have disabled it on an
     * earlier run. A disabled application is not returned by an ordinary lookup, so
     * without MATCH_DISABLED_COMPONENTS the option would disappear entirely rather than
     * come back unchecked.
     */
    private boolean isAuroraServicesPresent() {
        try {
            getPackageManager().getPackageInfo(
                    PACKAGE_AURORA_SERVICES, PackageManager.MATCH_DISABLED_COMPONENTS);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void toggleAdvanced() {
        boolean expanded = mAdvancedContainer.getVisibility() == View.VISIBLE;
        mAdvancedContainer.setVisibility(expanded ? View.GONE : View.VISIBLE);
        if (mAdvancedChevron != null) {
            mAdvancedChevron.setRotation(expanded ? 0f : 180f);
        }
    }

    /** The companion only does anything alongside Aurora Store itself. */
    private void updateAuroraServicesEnabled() {
        if (mAuroraServicesContainer == null || mIncludeAuroraServicesCheckBox == null) {
            return;
        }
        boolean enabled = mIncludeAuroraCheckBox != null && mIncludeAuroraCheckBox.isChecked();
        mAuroraServicesContainer.setEnabled(enabled);
        mAuroraServicesContainer.setAlpha(enabled ? 1f : DISABLED_ALPHA);
        mIncludeAuroraServicesCheckBox.setEnabled(enabled);
        if (!enabled) {
            mIncludeAuroraServicesCheckBox.setChecked(false);
        }
    }

    @Override
    protected void onNextPressed() {
        launchMicroGSetup();
    }

    @Override
    protected void onSubactivityResult(ActivityResult activityResult) {
        int resultCode = activityResult.getResultCode();
        Intent data = activityResult.getData();
        if (resultCode != RESULT_CANCELED) {
            finishAction(resultCode, data);
        } else if (mIsSubactivityNotFound) {
            finishAction(RESULT_ACTIVITY_NOT_FOUND);
        } else if (data != null && data.getBooleanExtra("onBackPressed", false)) {
            onStartSubactivity();
        } else {
            finishAction(RESULT_SKIP);
        }
    }

    private void launchMicroGSetup() {
        Intent intent = new Intent(ACTION_MICROG_SETUP);
        intent.putExtra(EXTRA_INCLUDE_AURORA, isChecked(mIncludeAuroraCheckBox));
        intent.putExtra(EXTRA_INCLUDE_GSF, isChecked(mIncludeGsfCheckBox));
        intent.putExtra(EXTRA_INCLUDE_AURORA_SERVICES,
                isChecked(mIncludeAuroraServicesCheckBox)
                        && mAuroraServicesContainer != null
                        && mAuroraServicesContainer.getVisibility() == View.VISIBLE);
        startSubactivity(intent);
    }

    private static boolean isChecked(CheckBox checkBox) {
        return checkBox != null && checkBox.isChecked();
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_microg;
    }

    @Override
    protected int getTitleResId() {
        return R.string.setup_microg_title;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_restore;
    }
}
