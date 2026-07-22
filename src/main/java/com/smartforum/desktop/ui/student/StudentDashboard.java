package com.smartforum.desktop.ui.student;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.ui.common.*;

import javax.swing.*;
import java.util.List;

public class StudentDashboard {

    public static DashboardChrome build(AppContext ctx, Runnable onLogout) {
        List<NavItem> navItems = List.of(
                new NavItem("groups", "\uD83D\uDC65", "Groups"),
                new NavItem("grades", "\uD83C\uDF93", "My Grades"),
                new NavItem("quizzes", "\uD83D\uDCDD", "Quizzes"),
                new NavItem("recommended", "\u2728", "Recommended"),
                new NavItem("notifications", "\uD83D\uDD14", "Notifications"),
                new NavItem("profile", "\uD83D\uDC64", "My Profile")
        );

        DashboardChrome chrome = new DashboardChrome(ctx, navItems, onLogout);

        TopicWorkspacePanel topicWorkspace = new TopicWorkspacePanel(ctx);
        // A student who is also a group admin/owner for one of their groups
        // can view that group's Statistics and Gradebook (GroupsPanel gates
        // the buttons on canViewStats, not on role), so these need real
        // navigation here too, not no-ops.
        GroupStatisticsPanel statsPanel = new GroupStatisticsPanel(ctx, () -> chrome.showPanel("groups"));
        GroupGradebookPanel gradebookPanel = new GroupGradebookPanel(ctx, () -> chrome.showPanel("groups"));

        GroupsPanel groupsPanel = new GroupsPanel(ctx, GroupsPanel.Mode.STUDENT,
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

        MyGradePanel gradesPanel = new MyGradePanel(ctx);
        QuizListPanel quizzesPanel = new QuizListPanel(ctx, false);
        RecommendedTopicsPanel recommendedPanel = new RecommendedTopicsPanel(ctx, topicId -> {
            // Recommendations link straight to a topic; without its group_id
            // in hand here, send the person back to Groups to navigate in -
            // still one click away, and avoids guessing an unknown group id.
            chrome.showPanel("groups");
            JOptionPane.showMessageDialog(chrome, "Open the topic's group from your Groups list to jump straight to it.");
        });
        NotificationsPanel notificationsPanel = new NotificationsPanel(ctx);
        ProfilePanel profilePanel = new ProfilePanel(ctx);

        chrome.addPanel("groups", groupsPanel);
        chrome.addPanel("topics", topicWorkspace);
        chrome.addPanel("group-statistics", statsPanel);
        chrome.addPanel("group-gradebook", gradebookPanel);
        chrome.addPanel("grades", gradesPanel);
        chrome.addPanel("quizzes", quizzesPanel);
        chrome.addPanel("recommended", recommendedPanel);
        chrome.addPanel("notifications", notificationsPanel);
        chrome.addPanel("profile", profilePanel);

        // Refresh each panel's data every time its nav item is clicked, so
        // panels don't show stale data left over from earlier in the session.
        chrome.onNavigateTo("groups", groupsPanel::refresh);
        chrome.onNavigateTo("grades", gradesPanel::refresh);
        chrome.onNavigateTo("quizzes", quizzesPanel::refreshMine);
        chrome.onNavigateTo("recommended", recommendedPanel::refresh);
        chrome.onNavigateTo("notifications", notificationsPanel::refresh);
        chrome.onNavigateTo("profile", profilePanel::refresh);

        chrome.showPanel("groups");

        return chrome;
    }
}