import com.google.gson.Gson;
import core.*;
import core.api.*;
import core.api.commands.Direction;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public class MyBot implements Bot {

    InitialData data;

    Coin kovanec0;
    Coin kovanec1;
    int cilj; // 0 => kovanec0, 1 => kovanec1

    ArrayList<Vozlisce> vrsta = new ArrayList<Vozlisce>();
    ArrayList<Vozlisce> potDoResitve = new ArrayList<Vozlisce>();

    // Called only once before the match starts. It holds the
    // data that you may need before the game starts.
    @Override
    public void setup(InitialData data) {
        System.out.println((new Gson()).toJson(data));
        this.data = data;

        // Print out the map
        for (int y = data.mapHeight - 1; y >= 0; y--) {
            for (int x = 0; x < data.mapWidth; x++) {
                System.out.print((data.map[y][x]) ? "_" : "#");
            }
            System.out.println();
        }

        kovanec0 = new Coin(-1, -1);
        kovanec1 = new Coin(-1, -1);
    }

    private Saw naslednjeStanjeZage(Saw saw) {
        SawDirection smer = saw.direction;
        int x = saw.x;
        int y = saw.y;

        if (smer == SawDirection.DOWN_LEFT) {
            if (x == 0 && y == 0) {
                x++;
                y++;
                smer = SawDirection.UP_RIGHT;
            } else if (x == 0) {
                x++;
                y--;
                smer = SawDirection.DOWN_RIGHT;
            } else if (y == 0) {
                x--;
                y++;
                smer = SawDirection.UP_LEFT;
            } else {
                x--;
                y--;
            }
            return new Saw(x,y,smer);
        } else if (smer == SawDirection.DOWN_RIGHT) {
            if (x == data.mapWidth-1 && y == 0) {
                x--;
                y++;
                smer = SawDirection.UP_LEFT;
            } else if (x == data.mapWidth-1) {
                x--;
                y--;
                smer = SawDirection.DOWN_LEFT;
            } else if (y == 0) {
                x++;
                y++;
                smer = SawDirection.UP_RIGHT;
            } else {
                x++;
                y--;
            }
            return new Saw(x,y,smer);
        } else if (smer == SawDirection.UP_LEFT) {
            if (x == 0 && y == data.mapHeight-1) {
                x++;
                y--;
                smer = SawDirection.DOWN_RIGHT;
            } else if (x == 0) {
                x++;
                y++;
                smer = SawDirection.UP_RIGHT;
            } else if (y == data.mapHeight-1) {
                x--;
                y--;
                smer = SawDirection.DOWN_LEFT;
            } else {
                x--;
                y++;
            }
            return new Saw(x,y,smer);
        } else if (smer == SawDirection.UP_RIGHT) {
            if (x == data.mapWidth-1 && y == data.mapHeight-1) {
                x--;
                y--;
                smer = SawDirection.DOWN_LEFT;
            } else if (x == data.mapWidth-1) {
                x--;
                y++;
                smer = SawDirection.UP_LEFT;
            } else if (y == data.mapHeight-1) {
                x++;
                y--;
                smer = SawDirection.DOWN_RIGHT;
            } else {
                x++;
                y++;
            }
            return new Saw(x,y,smer);
        } else {
            return new Saw(x,y,smer);
        }
    }

    // Called repeatedly while the match is generating. Each
    // time you receive the current match state and can use
    // response object to issue your commands.
    @Override
    public void update(MatchState state, Response response) {
        // preverimo, ali se je kateri od kovancev premaknil
        if (state.coins[0].x != kovanec0.x || state.coins[0].y != kovanec0.y ||
            state.coins[1].x != kovanec1.x || state.coins[1].y != kovanec1.y ||
            potDoResitve.isEmpty()) {

            kovanec0.x = state.coins[0].x;
            kovanec0.y = state.coins[0].y;
            kovanec1.x = state.coins[1].x;
            kovanec1.y = state.coins[1].y;

            dolociCiljniKovanecAgenta(state);
        }

        vrsta.clear();
        potDoResitve.clear();
        MatchState newState = cloneMatchState(state);
        poisciPot(newState, 0, 0, null);

        if (potDoResitve != null && !potDoResitve.isEmpty()) {
            if (potDoResitve.get(0) == null)
                potDoResitve.remove(0);
            potDoResitve.removeIf(new Predicate<Vozlisce>() {
                @Override
                public boolean test(Vozlisce vozlisce) {
                    if (vozlisce.x == state.yourUnit.x && vozlisce.y == state.yourUnit.y)
                        return true;
                    return false;
                }
            });
            Direction direction = pridobiSmerPremika(state.yourUnit.x, state.yourUnit.y, potDoResitve.get(0));
            potDoResitve.remove(0);
            response.moveUnit(direction);
        }
    }

    private MatchState cloneMatchState(MatchState state) {
        MatchState newState = new MatchState();
        newState.__uid = state.__uid;
        newState.yourUnit = new Unit(state.yourUnit.x, state.yourUnit.y, state.yourUnit.points, state.yourUnit.lives);
        newState.opponentUnit = new Unit(state.opponentUnit.x, state.opponentUnit.y, state.opponentUnit.points, state.opponentUnit.lives);
        newState.saws = state.saws.clone();
        newState.time = state.time;
        newState.coins = state.coins.clone();
        return newState;
    }

    /**
     * pregleda premike v vse 4 smeri in vrne smer najbolj ugodnega premika
     * @param state
     */
    private void poisciPot(MatchState state, int potDoSem, int globina, Vozlisce predhodnik) {
        if (preveriCeSmoNaCilju(state)) {
            potDoResitve.add(new Vozlisce(state.yourUnit.x, state.yourUnit.y));
            potDoResitve.add(0, predhodnik);
            return;
        }
        // ne garantira, da se izognemo StackOverflowError, saj ne vemo koliko sklada ima računalnik
        /*if (globina > 500) {
            potDoResitve.add(new Vozlisce(state.yourUnit.x, state.yourUnit.y));
            potDoResitve.add(0, predhodnik);
            return;
        }*/
        MatchState kopijaStanja = cloneMatchState(state);

        // v vrsto damo premike v vse 4 smeri
        vrsta.addAll(dodajPremikeVVrsto(kopijaStanja, potDoSem+1));
        // odstranimo vozlisca, do katerih smo prisli po vec poteh in ohranimo le tiste z nižjo ceno
        odstraniPodvojenaVozlisca();
        // generiraj polje kakršno bo v naslednji iteraciji
        boolean[][] polje = generirajPolje(kopijaStanja);
        // preveri veljavnost premikov in neveljavne izloči iz vrste
        preveriInIzloci(polje);

        // preveri, da nam je v vrsti ostalo kaj premikov in se nismo zaprli v brezizhodno stanje
        if (vrsta.isEmpty()) {
            return;
        }

        // uredi premike po naraščajoči ceni do cilja
        vrsta.sort(new Comparator<Vozlisce>() {
            @Override
            public int compare(Vozlisce o1, Vozlisce o2) {
                return Integer.compare(o1.cena, o2.cena);
            }
        });

        // vzamemo prvo vozlišče iz vrste in ga razvijemo
        do {
            Vozlisce v = vrsta.get(0);
            Vozlisce tmp = new Vozlisce(v.x, v.y, v.cena, v.potDoSem, v.vozlisce);
            vrsta.remove(0);

            MatchState naslednjeStanje = generirajNovMatchState(kopijaStanja,
                    ustvariSeznamZagVNaslednjiIteraciji(kopijaStanja.saws, kopijaStanja.time),
                    tmp);

            // nam je zmanjkalo sklada a še vseeno nismo našli rešitve
            // dodamo kar trenutno vozlišče, saj ima trenutno najboljšo oceno
            try {
                poisciPot(naslednjeStanje, tmp.potDoSem, globina + 1, tmp.vozlisce);
            } catch (StackOverflowError e) {
                potDoResitve.add(new Vozlisce(state.yourUnit.x, state.yourUnit.y));
                potDoResitve.add(0, predhodnik);
                return;
            }

            if (!potDoResitve.isEmpty()) {
                Vozlisce predhodnikResitev = potDoResitve.get(0);
                // preverimo če je to vozlišče iz katerega smo klicali, na poti do rešitve
                if (predhodnikResitev.x == state.yourUnit.x && predhodnikResitev.y == state.yourUnit.y) {
                    potDoResitve.add(0, predhodnik);
                    return;
                }
            }
        } while (potDoResitve.isEmpty() && !vrsta.isEmpty());
    }

    private boolean preveriCeSmoNaCilju(MatchState state) {
        if (cilj == 0) {
            if (state.yourUnit.x == kovanec0.x && state.yourUnit.y == kovanec0.y){
                return true;
            }
        } else if (cilj == 1) {
            if (state.yourUnit.x == kovanec1.x && state.yourUnit.y == kovanec1.y){
                return true;
            }
        }

        return false;
    }

    private MatchState generirajNovMatchState(MatchState state, ArrayList<Saw> sawArrayList, Vozlisce vozlisce) {
        Saw[] saws = sawArrayList.toArray(Saw[]::new);

        state.time += 0.5;
        state.yourUnit.x = vozlisce.x;
        state.yourUnit.y = vozlisce.y;
        state.saws = saws;

        return state;
    }

    private Direction pridobiSmerPremika(int x, int y, Vozlisce naslednje) {
        if (x < naslednje.x) {
            return Direction.RIGHT;
        } else if (x > naslednje.x) {
            return Direction.LEFT;
        } else if (y < naslednje.y) {
            return Direction.UP;
        } else {
            return Direction.DOWN;
        }
    }

    private ArrayList<Vozlisce> dodajPremikeVVrsto(MatchState state, int potDoSem) {
        // premike gor, dol, levo, desno dodamo na seznam in izračunamo njihovo ceno
        ArrayList<Vozlisce> vrsta = new ArrayList<Vozlisce>();

        Vozlisce predhodnik = new Vozlisce(state.yourUnit.x, state.yourUnit.y);

        if (cilj == 0) {
            vrsta.add(0, new Vozlisce(
                    state.yourUnit.x, state.yourUnit.y + 1,
                    potDoSem + hevristicnaManhattanskaRazdalja(
                            state.yourUnit.x,state.yourUnit.y +1,
                            kovanec0.x, kovanec0.y),
                    potDoSem, predhodnik)); // gor
            vrsta.add(0, new Vozlisce(state.yourUnit.x, state.yourUnit.y - 1,
                    potDoSem + hevristicnaManhattanskaRazdalja(
                            state.yourUnit.x,state.yourUnit.y - 1,
                            kovanec0.x, kovanec0.y),
                    potDoSem, predhodnik)); // dol
            vrsta.add(0, new Vozlisce(state.yourUnit.x - 1, state.yourUnit.y,
                    potDoSem + hevristicnaManhattanskaRazdalja(
                            state.yourUnit.x - 1, state.yourUnit.y,
                            kovanec0.x, kovanec0.y),
                    potDoSem, predhodnik)); // levo
            vrsta.add(0, new Vozlisce(state.yourUnit.x + 1, state.yourUnit.y,
                    potDoSem + hevristicnaManhattanskaRazdalja(
                            state.yourUnit.x + 1,state.yourUnit.y,
                            kovanec0.x, kovanec0.y),
                    potDoSem, predhodnik)); // desno
        } else {
            vrsta.add(0, new Vozlisce(
                    state.yourUnit.x, state.yourUnit.y + 1,
                    potDoSem + hevristicnaManhattanskaRazdalja(
                            state.yourUnit.x,state.yourUnit.y +1,
                            kovanec1.x, kovanec1.y),
                    potDoSem, predhodnik)); // gor
            vrsta.add(0, new Vozlisce(state.yourUnit.x, state.yourUnit.y - 1,
                    potDoSem + hevristicnaManhattanskaRazdalja(
                            state.yourUnit.x,state.yourUnit.y - 1,
                            kovanec1.x, kovanec1.y),
                    potDoSem, predhodnik)); // dol
            vrsta.add(0, new Vozlisce(state.yourUnit.x - 1, state.yourUnit.y,
                    potDoSem + hevristicnaManhattanskaRazdalja(
                            state.yourUnit.x - 1, state.yourUnit.y,
                            kovanec1.x, kovanec1.y),
                    potDoSem, predhodnik)); // levo
            vrsta.add(0, new Vozlisce(state.yourUnit.x + 1, state.yourUnit.y,
                    potDoSem + hevristicnaManhattanskaRazdalja(
                            state.yourUnit.x + 1,state.yourUnit.y,
                            kovanec1.x, kovanec1.y),
                    potDoSem, predhodnik)); // desno
        }
        return vrsta;
    }

    private void odstraniPodvojenaVozlisca() {
        ArrayList<Vozlisce> novaVrsta = new ArrayList<Vozlisce>();
        for (Vozlisce a : vrsta) {
            // preveri, če je v vrsti že element s temi koordinatami
            int index = vrstaVsebuje(novaVrsta, a);
            if (index > -1) {
                // preveri ali ima vozlisce "a" manjšo ceno kot vozlišče, ki je že v novi vrsti
                if (a.imaManjsoCeno(novaVrsta.get(index))) {
                    // odstrani vozlišče iz nove vrste in vanjo dodaj "a"
                    novaVrsta.remove(index);
                    novaVrsta.add(a);
                }
            } else {
                // tega elementa še ni v novi vrsti, lahko ga mirne duše dodamo
                novaVrsta.add(a);
            }
        }
        vrsta = novaVrsta;
    }

    private int vrstaVsebuje(ArrayList<Vozlisce> novaVrsta, Vozlisce a) {
        for (Vozlisce v : novaVrsta) {
            if (v.x == a.x && v.y == a.y) return novaVrsta.indexOf(v);
        }
        return -1;
    }

    // preveri veljavnost premika
    private void preveriInIzloci(boolean[][] polje) {
        vrsta.removeIf(v ->
                v.x < 0 ||
                v.y < 0 ||
                v.x >= data.mapWidth ||
                v.y >= data.mapHeight ||
                polje[v.y][v.x] == false);
    }

    private boolean[][] generirajPolje(MatchState state) {
        // nov objekt polje, nanj prerišemo začetno polje
        boolean[][] polje = new boolean[11][20];
        for (int i = 0; i < polje.length; i++) {
            for (int j = 0; j < polje[i].length; j++) {
                polje[i][j] = data.map[i][j];
            }
        }

        // zlij polje z žagami - preverjamo za naslednjo iteracijo
        return zlijZacetnoPoljeInZage(polje, state.saws, state.time);
    }

    private boolean[][] zlijZacetnoPoljeInZage(boolean[][] polje, Saw[] saws, float time) {
        // glede na trenutne pozicije žag, ustvarimo seznam s pozicijami žag, kjer bodo v naslednji iteraciji
        ArrayList<Saw> seznamZag = ustvariSeznamZagVNaslednjiIteraciji(saws, time);

        // polje igralne plošče kjer bodo žage, postavimo na false
        for (Saw saw : seznamZag) {
            polje[saw.y][saw.x] = false;
        }

        return polje;
    }

    private ArrayList<Saw> ustvariSeznamZagVNaslednjiIteraciji(Saw[] saws, float time) {
        ArrayList<Saw> seznamZag = new ArrayList<Saw>();
        // naredi premik žage in jo dodaj na seznam
        for (Saw saw : saws) {
            seznamZag.add(naslednjeStanjeZage(saw));
        }
        // preveri, če bosta v naslednji iteraciji generirani novi žagi
        if ((time+0.5) % 11 == 0) {
            // dodaj novi žagi na seznam
            seznamZag.add(new Saw(5,0,SawDirection.UP_RIGHT));
            seznamZag.add(new Saw(14,10,SawDirection.DOWN_LEFT));
        }
        return seznamZag;
    }

    // Connects your bot to match generator, don't change it.
    public static void main(String[] args) throws Exception {
        NetworkingClient.connectNew(args, new MyBot());
    }

    /**
     *
     * @param x0 x coordinate of first point
     * @param y0 y coordinate of first point
     * @param x1 x coordinate of second point
     * @param y1 y coordinate of second point
     * @return Manhattan distance between two given coordinates
     */
    private int hevristicnaManhattanskaRazdalja(int x0, int y0, int x1, int y1) {
        return (Math.abs(x0 - x1) + Math.abs(y0 - y1));
    }

    private void dolociCiljniKovanecAgenta(MatchState state) {
        int razdalja0 = hevristicnaManhattanskaRazdalja(state.yourUnit.x, state.yourUnit.y, kovanec0.x, kovanec0.y) +
                hevristicnaManhattanskaRazdalja(state.opponentUnit.x, state.opponentUnit.y, kovanec1.x, kovanec1.y);
        int razdalja1 = hevristicnaManhattanskaRazdalja(state.yourUnit.x, state.yourUnit.y, kovanec1.x, kovanec1.y) +
                hevristicnaManhattanskaRazdalja(state.opponentUnit.x, state.opponentUnit.y, kovanec0.x, kovanec0.y);
        cilj = razdalja0 < razdalja1 ? 0 : 1;
    }
}

class Vozlisce {
    public int x;
    public int y;
    public int cena;
    public int potDoSem;
    public Direction direction;
    public Vozlisce vozlisce;

    Vozlisce(int x, int y) {
        this.x = x;
        this.y = y;
    }

    Vozlisce(int x, int y, int cena, int potDoSem, Vozlisce vozlisce) {
        this.x = x;
        this.y = y;
        this.cena = cena;
        this.potDoSem = potDoSem;
        this.vozlisce = vozlisce;
    }

    Vozlisce(int x, int y, int cena, int potDoSem, Direction direction) {
        this.x = x;
        this.y = y;
        this.cena = cena;
        this.potDoSem = potDoSem;
        this.direction = direction;
    }

    public boolean imaManjsoCeno(Vozlisce vozlisce) {
        if (this.cena < vozlisce.cena) return true;
        return false;
    }
}