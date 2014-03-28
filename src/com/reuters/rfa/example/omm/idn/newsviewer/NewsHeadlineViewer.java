package com.reuters.rfa.example.omm.idn.newsviewer;

import java.awt.Component;
import java.awt.Font;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import com.reuters.rfa.example.framework.sub.SubAppContext;

/**
 * This class is responsible for building GUI to display news headline data.
 * 
 */
public class NewsHeadlineViewer
{
    JList _headlines;
    DefaultListModel _headlineModel;
    protected SubAppContext _appContext;
    JScrollPane _scroll;
    NewsFilterSelector _filterSelector;
    Vector<Headline> _headlinesList;

    public NewsHeadlineViewer(NewsStoryViewer storyViewer, Font font)
    {
        _appContext = storyViewer._appContext;
        _filterSelector = new NewsFilterSelector(this);
        _headlinesList = new Vector();
        _headlineModel = new DefaultListModel();
        _headlines = new JList(_headlineModel);
        _headlines.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        _headlines.setFont(font);
        _headlines.setFixedCellWidth(700);
        _headlines.addMouseListener(new NewsHeadlineListener(storyViewer));
        _scroll = new JScrollPane(_headlines);
        _scroll.setAutoscrolls(true);
    }

    Component component()
    {
        return _scroll;
    }

    void addHeadline(Headline headline)
    {
        if (_filterSelector.checkFilters(headline))
        {
            _headlineModel.add(0, headline);
        }
        _headlinesList.add(headline);
        _filterSelector._codeDb.addAttribution(headline.getAttribution());
        _filterSelector._codeDb.addCompanies(headline.getCompanyCodes());
        _filterSelector._codeDb.addProducts(headline.getProdCodes());
        _filterSelector._codeDb.addLanguage(headline.getLang());
        _filterSelector._codeDb.addTopics(headline.getTopicCodes());
    }

    void applyFilter()
    {
        _headlineModel.clear();

        Iterator<Headline> iter = _headlinesList.iterator();
        while (iter.hasNext())
        {
            Headline headline = iter.next();
            if (_filterSelector.checkFilters(headline))
            {
                _headlineModel.add(0, headline);
            }
        }
    }
}
