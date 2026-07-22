package com.smartforum.desktop.ui.lecturer;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.ui.common.*;

import java.util.List;

/**
 * Nav items match the real Learning section in layouts/app.blade.php for
 * the lecturer role exactly: Groups, Quizzes, Scoring Criteria,
 * Notifications, My Profile. There is no standalone "Gradebook" nav item
 * in Laravel - the gradebook is only reachable per-group, via a button on
 * that group's card in the Groups panel (GroupsPanel.Mode.LECTURER).
 *
 * Statistics and Gradebook are now full pages (their own CardLayout entries
 * in DashboardChrome) rather than popup dialogs - reached only by clicking
 * the Statistics/Gradebook button on a group row, same way "Open" drills
 * into the Topics page. They deliberately have no sidebar nav item of
 * their own, mirroring how Laravel's group-scoped statistics/gradebook
 * routes aren't in the main nav either.
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
        GroupStatisticsPanel statsPanel = new GroupStatisticsPanel(ctx, () -> chrome.showPanel("groups"));
        GroupGradebookPanel gradebookPanel = new GroupGradebookPanel(ctx, () -> chrome.showPanel("groups"));

        GroupsPanel groupsPanel = new GroupsPanel(ctx, GroupsPanel.Mode.LECTURER,
                groupId -> {
                    chrome.showPanel("topics");
                    topicWorkspace.openGroup(groupId, null);
                },
                groupId -> {
                    chrome.showPanel("group-statistics");
                    statsPanel.open(groupId);
                },
                groupId -> {
                    chrome.showPanel("group-gradebook");
                    gradebookPanel.open(groupId);
                });

        QuizListPanel quizzesPanel = new QuizListPanel(ctx, true);
        ScoringCriteriaPanel criteriaPanel = new ScoringCriteriaPanel(ctx);
        NotificationsPanel notificationsPanel = new NotificationsPanel(ctx);
        ProfilePanel profilePanel = new ProfilePanel(ctx);

        chrome.addPanel("groups", groupsPanel);
        chrome.addPanel("topics", topicWorkspace);
        chrome.addPanel("group-statistics", statsPanel);
        chrome.addPanel("group-gradebook", gradebookPanel);
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

        return chrome;
    }
}