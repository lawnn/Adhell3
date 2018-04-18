package com.fusionjack.adhell3.blocker;

import android.os.Handler;

import com.fusionjack.adhell3.db.AppDatabase;
import com.fusionjack.adhell3.db.entity.AppInfo;
import com.fusionjack.adhell3.db.entity.UserBlockUrl;
import com.fusionjack.adhell3.db.entity.WhiteUrl;
import com.fusionjack.adhell3.utils.AdhellFactory;
import com.fusionjack.adhell3.utils.BlockUrlPatternsMatch;
import com.fusionjack.adhell3.utils.BlockUrlUtils;
import com.fusionjack.adhell3.utils.LogUtils;
import com.google.common.collect.Lists;
import com.sec.enterprise.AppIdentity;
import com.sec.enterprise.firewall.DomainFilterRule;
import com.sec.enterprise.firewall.Firewall;
import com.sec.enterprise.firewall.FirewallResponse;
import com.sec.enterprise.firewall.FirewallRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

public class ContentBlocker56 implements ContentBlocker {
    private static ContentBlocker56 mInstance = null;

    private Firewall firewall;
    private AppDatabase appDatabase;
    private Handler handler;

    private ContentBlocker56() {
        this.appDatabase = AdhellFactory.getInstance().getAppDatabase();
        this.firewall = AdhellFactory.getInstance().getFirewall();
    }

    public static ContentBlocker56 getInstance() {
        if (mInstance == null) {
            mInstance = getSync();
        }
        return mInstance;
    }

    private static synchronized ContentBlocker56 getSync() {
        if (mInstance == null) {
            mInstance = new ContentBlocker56();
        }
        return mInstance;
    }

    @Override
    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    @Override
    public boolean enableBlocker() {
        if (firewall == null) {
            return false;
        }

        try {
            processCustomRules();
            processMobileRestrictedApps();
            processWhitelistedApps();
            processWhitelistedDomains();
            processBlockedDomains();

            if (!firewall.isFirewallEnabled()) {
                LogUtils.getInstance().writeInfo("\nEnabling firewall...", handler);
                firewall.enableFirewall(true);
            }
            if (!firewall.isDomainFilterReportEnabled()) {
                LogUtils.getInstance().writeInfo("Enabling firewall report...", handler);
                firewall.enableDomainFilterReport(true);
            }
            LogUtils.getInstance().writeInfo("\nFirewall is enabled.", handler);
        } catch (Exception e) {
            disableBlocker();
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void processCustomRules() throws Exception {
        LogUtils.getInstance().writeInfo("\nProcessing custom rules...", handler);

        int count = 0;
        List<UserBlockUrl> userBlockUrls = appDatabase.userBlockUrlDao().getAll2();
        for (UserBlockUrl userBlockUrl : userBlockUrls) {
            if (userBlockUrl.url.indexOf('|') != -1) {
                StringTokenizer tokens = new StringTokenizer(userBlockUrl.url, "|");
                if (tokens.countTokens() == 3) {
                    String packageName = tokens.nextToken();
                    String ipAddress = tokens.nextToken();
                    String port = tokens.nextToken();

                    // Define firewall rule
                    FirewallRule[] firewallRules = new FirewallRule[1];
                    firewallRules[0] = new FirewallRule(FirewallRule.RuleType.DENY, Firewall.AddressType.IPV4);
                    firewallRules[0].setIpAddress(ipAddress);
                    firewallRules[0].setPortNumber(port);
                    firewallRules[0].setApplication(new AppIdentity(packageName, null));

                    AdhellFactory.getInstance().addFirewallRules(firewallRules, handler);
                    ++count;
                }
            }
        }

        LogUtils.getInstance().writeInfo("Custom rule size: " + count, handler);
    }

    private void processMobileRestrictedApps() throws Exception {
        LogUtils.getInstance().writeInfo("\nProcessing mobile restricted apps...", handler);

        List<AppInfo> restrictedApps = appDatabase.applicationInfoDao().getMobileRestrictedApps();
        LogUtils.getInstance().writeInfo("Restricted apps size: " + restrictedApps.size(), handler);
        if (restrictedApps.size() == 0) {
            return;
        }

        // Define DENY rules for mobile data
        FirewallRule[] mobileRules = new FirewallRule[restrictedApps.size()];
        for (int i = 0; i < restrictedApps.size(); i++) {
            mobileRules[i] = new FirewallRule(FirewallRule.RuleType.DENY, Firewall.AddressType.IPV4);
            mobileRules[i].setNetworkInterface(Firewall.NetworkInterface.MOBILE_DATA_ONLY);
            mobileRules[i].setApplication(new AppIdentity(restrictedApps.get(i).packageName, null));
        }

        AdhellFactory.getInstance().addFirewallRules(mobileRules, handler);
    }

    private void processWhitelistedApps() throws Exception {
        LogUtils.getInstance().writeInfo("\nProcessing white-listed apps...", handler);

        // Create domain filter rule for white listed apps
        List<AppInfo> whitelistedApps = appDatabase.applicationInfoDao().getWhitelistedApps();
        LogUtils.getInstance().writeInfo("Whitelisted apps size: " + whitelistedApps.size(), handler);
        if (whitelistedApps.size() == 0) {
            return;
        }

        List<DomainFilterRule> rules = new ArrayList<>();
        List<String> superAllow = new ArrayList<>();
        superAllow.add("*");
        for (AppInfo app : whitelistedApps) {
            LogUtils.getInstance().writeInfo("Whitelisted app: " + app.packageName, handler);
            rules.add(new DomainFilterRule(new AppIdentity(app.packageName, null), new ArrayList<>(), superAllow));
        }
        AdhellFactory.getInstance().addDomainFilterRules(rules, handler);
    }

    private void processWhitelistedDomains() throws Exception {
        LogUtils.getInstance().writeInfo("\nProcessing white-listed domains...", handler);

        // Process user-defined white list
        // 1. URL for all packages: url
        // 2. URL for individual package: packageName|url
        List<WhiteUrl> whiteUrls = appDatabase.whiteUrlDao().getAll2();
        LogUtils.getInstance().writeInfo("User whitelisted URL size: " + whiteUrls.size(), handler);
        if (whiteUrls.size() == 0) {
            return;
        }

        Set<String> denyList = BlockUrlUtils.getUniqueBlockedUrls(appDatabase, handler, false);
        for (WhiteUrl whiteUrl : whiteUrls) {
            if (whiteUrl.url.indexOf('|') != -1) {
                StringTokenizer tokens = new StringTokenizer(whiteUrl.url, "|");
                if (tokens.countTokens() == 2) {
                    final String packageName = tokens.nextToken();
                    final String url = tokens.nextToken();
                    LogUtils.getInstance().writeInfo("PackageName: " + packageName + ", WhiteUrl: " + url, handler);

                    final AppIdentity appIdentity = new AppIdentity(packageName, null);
                    List<String> allowList = new ArrayList<>();
                    allowList.add(url);
                    processDomains(appIdentity, new ArrayList<>(denyList), allowList);
                }
            }
        }

        // Whitelist URL for all apps
        Set<String> allowList = new HashSet<>();
        for (WhiteUrl whiteUrl : whiteUrls) {
            if (whiteUrl.url.indexOf('|') == -1) {
                final String url = BlockUrlPatternsMatch.getValidatedUrl(whiteUrl.url);
                allowList.add(url);
                LogUtils.getInstance().writeInfo("WhiteUrl: " + url, handler);
            }
        }
        if (allowList.size() > 0) {
            final AppIdentity appIdentity = new AppIdentity("*", null);
            List<DomainFilterRule> rules = new ArrayList<>();
            rules.add(new DomainFilterRule(appIdentity, new ArrayList<>(), new ArrayList<>(allowList)));
            AdhellFactory.getInstance().addDomainFilterRules(rules, handler);
        }
    }

    private void processBlockedDomains() throws Exception {
        LogUtils.getInstance().writeInfo("\nProcessing blocked domains...", handler);

        Set<String> denyList = BlockUrlUtils.getUniqueBlockedUrls(appDatabase, handler, true);
        final AppIdentity appIdentity = new AppIdentity("*", null);
        processDomains(appIdentity, new ArrayList<>(denyList), new ArrayList<>());
    }

    private void processDomains(AppIdentity appIdentity, List<String> denyList, List<String> allowList) throws Exception {
        int start = 0;
        List<List<String>> chunks = Lists.partition(denyList, 5000);
        for (List<String> chunk : chunks) {
            LogUtils.getInstance().writeInfo("\nProcessing " + start + " to " + (start + chunk.size()) + " domains...", handler);
            start += chunk.size();

            List<DomainFilterRule> rules = new ArrayList<>();
            rules.add(new DomainFilterRule(appIdentity, chunk, allowList));
            AdhellFactory.getInstance().addDomainFilterRules(rules, handler);
        }
    }

    @Override
    public boolean disableBlocker() {
        if (firewall == null) {
            return false;
        }

        FirewallResponse[] response;
        try {
            // Clear firewall rules
            LogUtils.getInstance().writeInfo("\nClearing firewall rules...", handler);
            response = firewall.clearRules(Firewall.FIREWALL_ALL_RULES);
            LogUtils.getInstance().writeInfo(response == null ? "No response" : response[0].getMessage(), handler);

            // Clear domain filter rules
            LogUtils.getInstance().writeInfo("\nClearing domain rules...", handler);
            response = firewall.removeDomainFilterRules(DomainFilterRule.CLEAR_ALL);
            LogUtils.getInstance().writeInfo(response == null ? "No response" : response[0].getMessage(), handler);

            if (firewall.isFirewallEnabled()) {
                firewall.enableFirewall(false);
            }
            if (firewall.isDomainFilterReportEnabled()) {
                firewall.enableDomainFilterReport(false);
            }

            LogUtils.getInstance().writeInfo("\nFirewall is disabled.", handler);
        } catch (SecurityException ex) {
            LogUtils.getInstance().writeError("\nFailed to disable firewall: " + ex.getMessage(), ex, handler);
            return false;
        } finally {
            LogUtils.getInstance().close();
        }

        return true;
    }

    @Override
    public boolean isEnabled() {
        return firewall != null && firewall.isFirewallEnabled();
    }
}