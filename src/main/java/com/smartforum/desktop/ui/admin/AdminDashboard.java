package com.smartforum.desktop.ui.admin;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.ui.common.*;

import java.util.List;



public class AdminDashboard {

    public static DashboardChrome build(AppContext ctx, Runnable onLogout) {
        List<NavItem> navItems = List.of(
                new NavItem("overview", "\uD83D\uDCC8", "System Overview"),
                new NavItem("groups", "\uD83D\uDC65", "Groups"),
                new NavItem("warnings", "\u26A0\uFE0F", "Inactivity Warnings"),
                new NavItem("blacklists", "\uD83D\uDEAB", "Blacklisted Users"),
                new NavItem("users", "\uD83D\uDD11", "Manage Users"),
                new NavItem("profile", "\uD83D\uDC64", "My Profile")
        );

        DashboardChrome chrome = new DashboardChrome(ctx, navItems, onLogout);

        SystemStatisticsPanel overviewPanel = new SystemStatisticsPanel(ctx);
        GroupStatisticsPanel statsPanel = new GroupStatisticsPanel(ctx, () -> chrome.showPanel("groups"));
        GroupGradebookPanel gradebookPanel = new GroupGradebookPanel(ctx, () -> chrome.showPanel("groups"));

        // Administrators never open a group's messages - GroupsPanel's
        // ADMIN mode ignores this callback entirely (only Statistics/
        // Gradebook pages are reachable), so a no-op is safe here.
        GroupsPanel groupsPanel = new GroupsPanel(ctx, GroupsPanel.Mode.ADMIN,
                groupId -> {},
                groupId -> {
                    chrome.showPanel("group-statistics");
                    statsPanel.open(groupId);
                },
                groupId -> {
                    chrome.showPanel("group-gradebook");
                    gradebookPanel.open(groupId);
                });

        WarningsPanel warningsPanel = new WarningsPanel(ctx);
        BlacklistsPanel blacklistsPanel = new BlacklistsPanel(ctx);
        UserManagementPanel usersPanel = new UserManagementPanel(ctx);
        ProfilePanel profilePanel = new ProfilePanel(ctx);

        chrome.addPanel("overview", overviewPanel);
        chrome.addPanel("groups", groupsPanel);
        chrome.addPanel("group-statistics", statsPanel);
        chrome.addPanel("group-gradebook", gradebookPanel);
        chrome.addPanel("warnings", warningsPanel);
        chrome.addPanel("blacklists", blacklistsPanel);
        chrome.addPanel("users", usersPanel);
        chrome.addPanel("profile", profilePanel);

        chrome.onNavigateTo("overview", overviewPanel::refresh);
        chrome.onNavigateTo("groups", groupsPanel::refresh);
        chrome.onNavigateTo("warnings", warningsPanel::refresh);
        chrome.onNavigateTo("blacklists", blacklistsPanel::refresh);
        chrome.onNavigateTo("users", usersPanel::refresh);
        chrome.onNavigateTo("profile", profilePanel::refresh);

        chrome.showPanel("overview");

        return chrome;
    }
}
