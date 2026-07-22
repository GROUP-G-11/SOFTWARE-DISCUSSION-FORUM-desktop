package com.smartforum.desktop.ui.lecturer;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.ui.common.*;
import javax.swing.*;
import java.util.List;

/**
 * Nav items match the real Learning section in layouts/app.blade.php for
 * the lecturer role exactly: Groups, Quizzes, Scoring Criteria,
 * Notifications, My Profile. There is no standalone "Gradebook" nav item
 * in Laravel - the gradebook is only reachable per-group, via a button on
 * that group's card in the Groups panel (GroupsPanel.Mode.LECTURER).
 */
public class LecturerDashboard {

    public static DashboardChrome build(AppContext ctx, Runnable onLogout) {
        List<NavItem> navItems = List.of(
                new NavItem("groups", "\uD83D\uDC65", "Groups"),
                new NavItem("quizzes", "\uD83D\uDCDD", "Quizzes"),
                new NavItem("criteria", "\uD83D\uDCCA", "Scoring Criteria"),
                new NavItem("notifications", "\uD83D\uDD14", "Notifications"),
                new NavItem("profile", "\uD83D\uDC64", "My Profile")
        );

        DashboardChrome chrome = new DashboardChrome(ctx, navItems, onLogout);

        TopicWorkspacePanel topicWorkspace = new TopicWorkspacePanel(ctx);
        GroupsPanel groupsPanel = new GroupsPanel(ctx, GroupsPanel.Mode.LECTURER, groupId -> {
            chrome.showPanel("topics");
            topicWorkspace.openGroup(groupId, null);
        });
        QuizListPanel quizzesPanel = new QuizListPanel(ctx, true);
        ScoringCriteriaPanel criteriaPanel = new ScoringCriteriaPanel(ctx);
        NotificationsPanel notificationsPanel = new NotificationsPanel(ctx);
        ProfilePanel profilePanel = new ProfilePanel(ctx);

        chrome.addPanel("groups", groupsPanel);
        chrome.addPanel("topics", topicWorkspace);
        chrome.addPanel("quizzes", quizzesPanel);
        chrome.addPanel("criteria", criteriaPanel);
        chrome.addPanel("notifications", notificationsPanel);
        chrome.addPanel("profile", profilePanel);

        chrome.onNavigateTo("groups", groupsPanel::refresh);
        chrome.onNavigateTo("quizzes", quizzesPanel::refreshManaged);
        chrome.onNavigateTo("criteria", criteriaPanel::refresh);
        chrome.onNavigateTo("notifications", notificationsPanel::refresh);
        chrome.onNavigateTo("profile", profilePanel::refresh);

        chrome.showPanel("groups");

        pollUnreadNotifications(ctx, chrome);

        return chrome;
    }

    private static void pollUnreadNotifications(AppContext ctx, DashboardChrome chrome) {
        Runnable check = () -> new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                try {
                    return ctx.api.unreadNotificationCount();
                } catch (Exception e) {
                    return -1; // offline/error: leave badge as-is rather than flashing to 0
                }
            }

            @Override
            protected void done() {
                try {
                    int count = get();
                    if (count >= 0) chrome.setBadgeCount("notifications", count);
                } catch (Exception ignored) {
                }
            }
        }.execute();

        check.run();
        new javax.swing.Timer(20000, e -> check.run()).start();
    }
}
