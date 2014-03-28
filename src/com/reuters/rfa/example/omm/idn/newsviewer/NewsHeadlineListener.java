package com.reuters.rfa.example.omm.idn.newsviewer;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JList;

/**
 * This class handles mouse events
 */
public class NewsHeadlineListener extends MouseAdapter
{
    private final NewsStoryViewer _viewer;

    NewsHeadlineListener(NewsStoryViewer viewer)
    {
        _viewer = viewer;
    }

    public void mouseClicked(MouseEvent e)
    {
        if (e.getClickCount() == 2)
        {
            JList list = (JList)e.getComponent();
            int index = list.locationToIndex(e.getPoint());
            if (index != -1)
            {
                Headline hl = (Headline)list.getModel().getElementAt(index);
                _viewer.openStory(hl._lang, hl._pnac, hl._text);
            }
        }
    }
}
