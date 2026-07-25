package com.smartforum.desktop.ui.common;
 
import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;
 
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
 
/**
 * Lets a group admin/owner review pending join requests (GroupJoinRequest
 * rows) and approve or decline each one. Mirrors the web client's
 * loadJoinRequests()/resolveJoinRequest() in the Group Admin panel.
 */
public class JoinRequestsDialog extends JDialog {
 
    private final AppContext ctx;
    private final long groupId;
    private final JPanel listBody = new JPanel();
    private final Runnable onResolved; // lets the parent GroupsPanel refresh its own list too
 
    public JoinRequestsDialog(Window owner, AppContext ctx, long groupId, String groupName, Runnable onResolved) {
        super(owner, groupName + " \u2014 Join Requests", ModalityType.APPLICATION_MODAL);
        this.ctx = ctx;
        this.groupId = groupId;
        this.onResolved = onResolved;
 
        setResizable(false);
        getContentPane().setBackground(Theme.WHITE);
        setLayout(new BorderLayout());
 
        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setBackground(Theme.WHITE);
        card.setBorder(new EmptyBorder(24, 26, 20, 26));
 
        JLabel title = new JLabel("Pending join requests");
        title.setFont(Theme.HEADING_FONT_SM);
        title.setForeground(Theme.INK);
        card.add(title, BorderLayout.NORTH);
 
        listBody.setLayout(new BoxLayout(listBody, BoxLayout.Y_AXIS));
        listBody.setBackground(Theme.WHITE);
 
        JScrollPane scroll = new JScrollPane(listBody);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(400, 300));
        card.add(scroll, BorderLayout.CENTER);
 
        JButton close = Buttons.secondary("Close");
        close.addActionListener(e -> dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        footer.add(close);
        card.add(footer, BorderLayout.SOUTH);
 
        add(card, BorderLayout.CENTER);
        setSize(460, 420);
        setLocationRelativeTo(owner);
 
        refresh();
    }
 
    private void refresh() {
        listBody.removeAll();
        JLabel loading = new JLabel("Loading\u2026");
        loading.setFont(Theme.BODY_FONT);
        loading.setForeground(Theme.MUTED);
        listBody.add(loading);
        listBody.revalidate();
        listBody.repaint();
 
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    return ctx.api.groupJoinRequests(groupId);
                } catch (ApiException | ApiOfflineException e) {
                    return new JSONArray();
                }
            }
 
            @Override
            protected void done() {
                JSONArray requests;
                try {
                    requests = get();
                } catch (Exception e) {
                    requests = new JSONArray();
                }
                render(requests);
            }
        }.execute();
    }
 
    private void render(JSONArray requests) {
        listBody.removeAll();
 
        if (requests.isEmpty()) {
            JLabel empty = new JLabel("No pending join requests right now.");
            empty.setFont(Theme.BODY_FONT);
            empty.setForeground(Theme.MUTED);
            listBody.add(empty);
        } else {
            for (int i = 0; i < requests.length(); i++) {
                listBody.add(requestRow(requests.getJSONObject(i)));
            }
        }
        listBody.revalidate();
        listBody.repaint();
    }
 
    private JComponent requestRow(JSONObject request) {
        long requestId = request.optLong("join_request_id", -1);
        JSONObject user = request.optJSONObject("user");
        String name = user != null ? user.optString("full_name", "Unknown user") : "Unknown user";
 
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(Theme.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                new EmptyBorder(10, 4, 10, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
 
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(Theme.BODY_FONT_BOLD);
        nameLabel.setForeground(Theme.INK);
        row.add(nameLabel, BorderLayout.WEST);
 
        JButton approve = Buttons.primary("Approve");
        approve.addActionListener(e -> resolve(requestId, true));
        JButton decline = Buttons.secondary("Decline");
        decline.addActionListener(e -> resolve(requestId, false));
 
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(approve);
        actions.add(decline);
        row.add(actions, BorderLayout.EAST);
 
        return row;
    }
 
    private void resolve(long requestId, boolean approve) {
        new SwingWorker<Void, Void>() {
            String error = null;
 
            @Override
            protected Void doInBackground() {
                try {
                    if (approve) {
                        ctx.api.approveJoinRequest(groupId, requestId);
                    } else {
                        ctx.api.declineJoinRequest(groupId, requestId);
                    }
                } catch (ApiOfflineException e) {
                    error = "This needs an internet connection. Please try again once you're back online.";
                } catch (ApiException e) {
                    error = e.getMessage();
                }
                return null;
            }
 
            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(JoinRequestsDialog.this, error, "Couldn't resolve request", JOptionPane.WARNING_MESSAGE);
                } else {
                    refresh();
                    if (onResolved != null) onResolved.run();
                }
            }
        }.execute();
    }
}
 