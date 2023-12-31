package com.example.collectomon;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchFragment extends Fragment {
    private ListView listViewArtists;
    private List<String> artistNames;
    private ArrayAdapter<String> arrayAdapter;
    public Button addArtist, deleteArtist, continueButton;
    EditText artistName;
    Toolbar toolbar;
    DrawerLayout drawerLayout;
    NavigationView navigationView;
    ActionBarDrawerToggle drawerToggle;
    private static final String PREFS_FILE_NAME = "MyPrefsFile";
    private static final String ARTIST_KEY = "artist";
    private SharedPreferences sharedPreferences;
    private int checkedPosition = -1;
    Spinner spinnerArtists;
    CustomSpinnerAdapter spinnerAdapter;
    Context context;
    EditText searchEditText;
    ImageButton addCards;
    private RecyclerView recyclerView;
    private CardAdapter cardAdapter;
    private List<CardItem> cardItems;
    private CardDatabase db;
    private AppCompatActivity activity;
    String name;


    private final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            String searchText = s.toString().toLowerCase().trim();

            if (!searchText.isEmpty()) {
                filterCardItems(searchText);
            } else {
                cardAdapter.filterList(cardItems);
            }
        }
    };




    public void addArtistToList(String name) {
        artistNames.add(name);
        arrayAdapter.notifyDataSetChanged();
        artistName.setText("");
        listViewArtists.clearChoices();
        checkedPosition = -1;
        saveArtistList(artistNames);
    }

    private void saveArtistList(List<String> artistList) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Set<String> set = new HashSet<>(artistList);
        editor.putStringSet(ARTIST_KEY, set);
        editor.apply();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        activity = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_search, container, false);
        context = requireContext();
        db = new CardDatabase(context);

        addCards = rootView.findViewById(R.id.addCardButton);
        sharedPreferences = requireActivity().getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE);
        artistNames = new ArrayList<>();
        Set<String> artistSet = sharedPreferences.getStringSet(ARTIST_KEY, null);
        if (artistSet != null) {
            artistNames = new ArrayList<>(artistSet);
        }

        spinnerArtists = rootView.findViewById(R.id.spinnerArtists);
        spinnerAdapter = new CustomSpinnerAdapter(context, artistNames);
        spinnerArtists.setAdapter(spinnerAdapter);

        searchEditText = rootView.findViewById(R.id.searchCard);
        searchEditText.addTextChangedListener(textWatcher);
        recyclerView = rootView.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        cardItems = new ArrayList<>();
        cardAdapter = new CardAdapter(cardItems, context);
        recyclerView.setAdapter(cardAdapter);

        spinnerArtists.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                webScrape(spinnerArtists.getSelectedItem().toString());
                if (!searchEditText.getText().toString().equals("")) {
                    searchEditText.setText("");
                }


            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Handle case where no item is selected (optional)
            }
        });

        addCards.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onClick(View v) {
                List<CardItem> selectedCardItems = cardAdapter.getSelectedCardItems();
                cardAdapter.notifyDataSetChanged();
                db.addCards(selectedCardItems);
                Toast.makeText(context, "Cards have been added!", Toast.LENGTH_SHORT).show();
                pulseAnimation();
            }
        });

        return rootView;
    }

    private void filterCardItems(String searchText) {
        List<CardItem> filteredList = new ArrayList<>();

        for (CardItem cardItem : cardItems) {
            if (cardItem.getCardName().toLowerCase().startsWith(searchText.toLowerCase())) {
                filteredList.add(cardItem);
            }
        }
        cardAdapter.filterList(filteredList);
    }

    public void webScrape(String name) {
        cardItems.clear();
        String stringWithoutGaps = name.replaceAll("\\s+", "");
        String modifiedName = stringWithoutGaps.toLowerCase();
        String theLink = "https://www.serebii.net/card/dex/artist/" + modifiedName + ".shtml";
        String finalTheLink = theLink;

        System.out.println(finalTheLink);
        Thread webScrapingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Document doc = Jsoup.connect(finalTheLink).get();
                    Element tableElement = doc.select("table.dextable").first();
                    Elements rowElements = tableElement.select("tr");
                    ArrayList<CardItem> cards = new ArrayList<>();

                    cards.clear();
                    cardItems.clear();

                    for (int i = 1; i < rowElements.size(); i++) {
                        Element row = rowElements.get(i);
                        Elements columnElements = row.select("td");

                        if (columnElements.size() >= 3 && columnElements.get(0).selectFirst("a img") != null) {
                            Element imageLink = columnElements.get(0).selectFirst("a");
                            String imageSrc = (imageLink != null) ? imageLink.selectFirst("img").attr("src") : "";
                            String imageSrc1 = "https://www.serebii.net/" + imageSrc;
                            Element cardNameElement = columnElements.get(1).selectFirst("font");
                            String cardName = (cardNameElement != null) ? cardNameElement.text() : "";

                            if (cardName.equals("")) {
                                Elements aElements = columnElements.get(1).select("a");
                                    cardName = aElements.text();
                            }

                            Element setLink = columnElements.get(2).selectFirst("a");
                            String setDetails = (setLink != null) ? setLink.text() : "";

                            String cardDetails = columnElements.get(2).ownText();
                            String cardId = name + cardName + setDetails + cardDetails;
                            CardItem cardItem = new CardItem(name, cardId, imageSrc1, cardName, setDetails, cardDetails);

                            boolean isDuplicate = false;
                            for (CardItem card : cards) {
                                if (card.getCardId().equals(cardItem.getCardId())) {
                                    isDuplicate = true;
                                    break;
                                }
                            }

                            if (!isDuplicate) {
                                cards.add(cardItem);
                                cardItems.add(cardItem);
                            }
                        }
                    }

                    if (activity != null) {
                        activity.runOnUiThread(new Runnable() {
                            @SuppressLint("NotifyDataSetChanged")
                            @Override
                            public void run() {
                                cardAdapter.notifyDataSetChanged();
                            }
                        });
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        webScrapingThread.start();
    }
    private void pulseAnimation() {
        ObjectAnimator scaleDown = ObjectAnimator.ofPropertyValuesHolder(
                addCards,
                PropertyValuesHolder.ofFloat("scaleX", 1.1f),
                PropertyValuesHolder.ofFloat("scaleY", 1.1f)
        );
        scaleDown.setDuration(500);
        scaleDown.setRepeatCount(ObjectAnimator.RESTART);
        scaleDown.setRepeatMode(ObjectAnimator.REVERSE);
        scaleDown.start();
    }
}
