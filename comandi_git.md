Git guida (in aggiornamento)
Cos'è git?
Git è un software di controllo versione distribuito utilizzabile da interfaccia a riga di comando, creato da Linus Torvalds nel 2005. Git lavora con i repository. Un repository git ha 4 stati di lavoro. Il primo è la tua directory corrente. Il secondo è l'index che fa da spazio di transito per i files (git add *). Il terzo è l'head che punta all'ultimo commit fatto (git commit -m "messaggio"). E l'ultimo è il repository è online (git push server).

I repository online e locali possono essere divisi in ramificazioni (Branch). I branch (ramificazioni) permettono di creare delle versioni assestanti del codice master. Queste versioni "assestanti" permettono la creazione di features o aggiornamenti in fase alpha che non vanno ad intaccare minimamente il codice del progetto. Finito la scrittura della ramificazione il branch verrà unito con il master
Git permette di gestire i tag. I tag sono le versioni del software in uso. I tag registrano tutti i commit fino al rilascio nel server.

Configurazioni di base di git

Configuriamo il nostro git con le nostre credenziali di GitHub:
  git config --global user.name 'Tuo Nome GitHub'
  git config --global user.email email@github.com

Ci sono due modi per instanzire un progetto
1.	Inizializziamo un progetto non esistente:
git init
2.	Inizializziamo un progetto esistente su un server git:

git clone serverURL.git 
Esempio: git clone https://github.com/tesseslol/irixos-websites.git Git clone permette di copiare il .git file del server e anche il repository.
Configurazione del server remoto

Con questo comando visualizziamo la lista di server remoti salvati con relativo url:
  git remote -v
P.S. di solito il server principale si chiama origin

Ora aggiungiamo un server remoto:
  git remote add identificatoreServerRemoto UrlServerRemoto
  Esempio: git remote add origin https://github.com/tesseslol/irixos-websites.git

Lavoriamo nel progetto:
Aggiungiamo i file dalla directory del progetto all'index:
  git add nome_file
Si può utilizzare l'asterisco per aggiungere tutti i file. Se si vuole escludere un file dalla selezione totale (con l'asterisco) basta creare un file denominato .gitignore e metterci all'interno i file che non si vogliono aggiungere al INDEX.

Ora aggiungiamo i file dell'index all'head:
  git commit -m "Messaggio del commit"

Per non tracciare il file usiamo l'argomento -a:
  git commit -a -m "Messaggio del commit"

Annullamento dei commit:
  git commit --amend

Cancellare un file da git:
  git rm nomeFile
Il file ritorna allo stato precedente dell’ultimo commit:
  git checkout -- nomeFile

Lavorare con il server remoto
Aggiornare il tuo repository locale alla commit più recente:
  git pull

Se vogliamo fare l'upload dei commit nel progetto usiamo:
  git push identificatoreServerRemoto nomeBranch
  Esempio: git push origin master

Se vogliamo rinominare un file in remoto:
  git remote rename identificatoreServerRemoto nomeFileVecchio nomeFileNuovo

Se vogliamo eliminare un file in remoto:
  git remote rm nomeFile
Stato del progetto

Per vedere le modifiche del progetto digitiamo:
  git status

Per vedere i cambiamenti dei singoli files digitiamo:
  git diff

Vedere tutti i commit:
  git log

Gestire i tag
Per visualizzare tutte le versioni eseguimo il comando:
  git tag

Per visualizzare tutte le versioni con un determinato numero:
  git tag -l 1*

Creazione di un tag:
  git tag -a versioneSoftware -m "nota sul tag"
  Esempio: git tag -a 1.2.3rc1 -m "aggiornato la navbar"

Vedere tutte le modifiche di un tag:
  git show 1.2.3rc1

Condividere i tag:
  git push identificatoreServerRemoto tagDaPubblicare
  Esempio: git push origin 1.2.3rc1 

Condividere tutti i tag:
  git push identificatoreServerRemoto --tag
  Esempio: git push origin --tag

Gestire i Branch
Lista dei Rami:
  git branch

Creiamo un branch con:
  git branch nomeBranch
  Esempio: git branch feature

Cambia i rami:
  git checkout nomeBranch
  Esempio: git checkout feature

Per ritornare al branch originale digitiamo:
  git checkout master

Eliminare il ramo:
  git branch -d nomeBranch
  Esempio: git branch -d feature

Crea il ramo e passa a quel branch:
  git checkout -b nomeBranch
  Esempio: git checkout -b feature

Per unire il branch al repository originale usiamo (ricordatevi di fare un commit nel branch):
  git checkout master
  git merge feature

Git Parameters:
*** Inizializza l'area di lavoro ***
 clone      Clona un repository in una cartella
 init       Crea un git repository o ne inizializza uno
*** Lavorare nel progetto corrente ***
  add        Aggiungere i file nel INDEX
  mv         Muove o rinomina un file, una directory
  reset      Resetta il corrente HEAD nello stato specificato
  rm         Rimuove i file dalla directory corrente e nel INDEX
*** Mostra la cronologia e lo stato ***
  bisect     Use binary search to find the commit that introduced a bug
  grep       Print lines matching a pattern
  log        Mostra i commit log
  status     stato del contenuto di un progetto
  show       Show various types of objects
*** Grow, mark and tweak your common history ***
  branch     Visualizza, crea e elimina ramo (branches)
  checkout   Cambia ramo (branches) o ripristina la strotura dell'area di lavoro 
  commit     Registra le modifiche del repository
  diff       Confronta i commit (esp: commit e area di lovoro)
  merge      Unisce una o più cronologie di sviluppo
  rebase     Reapply commits on top of another base tip
  tag        Crea, visualizza la lista, elimina o verifica il tag della versione del progetto
*** Collabora ***
  fetch      Download objects and refs from another repository
  pull       Fetch from and integrate with another repository or a local branch
  push       Update remote refs along with associated objects


creazione di un nuovo repository
crea una nuova directory, entraci ed esegui
git init
per creare un nuovo repository git.

checkout di un repository
crea una copia di un repository locale eseguendo il comando
git clone /percorso/del/repository
usando invece un server remoto, il comando sarà
git clone nomeutente@host:/percorso/del/repository

ambiente di lavoro
la tua copia locale del repository è composta da tre "alberi" mantenuti da git. Il primo è la tua Directory di lavoro che contiene i files attuali. Il secondo è l'Index che fa da spazio di transito per i files e per finire l'HEAD che punta all'ultimo commit fatto.


aggiungere & validare
Puoi proporre modifiche (aggiungendole all'Index) usando
git add <nomedelfile>
git add *
Questo è il primo passo nel flusso di lavoro in git. Per validare queste modifiche fatte si usa
git commit -m "Messaggio per la commit"
Ora il file è correttamente nell'HEAD, ma non ancora nel repository remoto.

invio delle modifiche
Quello che hai cambiato ora è nell'HEAD della copia locale. Per inviare queste modifiche al repository remoto, esegui
git push origin master
Cambia master nel branch al quale vuoi inviare i cambiamenti.

Se non hai copiato un repository esistente, e vuoi connettere il tuo repository ad un server remoto, c'e' bisogno che tu lo aggiunga con
git remote add origin <server>
Ora sarai in grado di inviare le tue modifiche al server remoto specificato
branching
I branch ('ramificazioni') sono utilizzati per sviluppare features che sono isolate l'una dall'altra. Il branch master è quello di default quando crei un repository. Puoi usare altri branch per lo sviluppo ed infine incorporarli ('merge') nel master branch una volta completati.


crea un nuovo branch chiamato "feature_x" e passa al nuovo branch usando
git checkout -b feature_x
ritorna di nuovo su master
git checkout master
e cancella il branch creato in precedenza
git branch -d feature_x
il branch non sarà disponibile agli altri fino a quando non verrà inviato al repository remoto

git push origin <branch>

aggiorna & incorpora
per aggiornare il tuo repository locale alla commit più recente, esegui
git pull
nella tua directory corrente per fare una fetch (recuperare) ed incorporare(merge) le modifiche fatte sul server remoto.
per incorporare un altro branch nel tuo branch attivo (ad esempio master), utilizza
git merge <branch>
in entrambi i casi git prova ad auto-incorporare le modifiche. Sfortunatamente, a volte questa procedura automatizzata non è possibile, ed in questo caso ci saranno dei conflitti. Sei tu il responsabile che sistemerà questi conflitti manualmente modificando i file che git mostrerà. Dopo aver cambiato questi files, dovrai marcarli come 'correttamente incorporati' tramite
git add <nomedelfile>
prima di immettere le modifiche, potrai anche visualizzarne un'anteprima eseguendo
git diff <branch_sorgente> <branch_target>

tags
È raccomandato creare dei tags nel caso in cui il software venga rilasciato. Questo è un concept già conosciuto, che esiste anche in SVN. Puoi creare un tag chiamato 1.0.0 eseguendo
git tag 1.0.0 1b2e1d63ff
la sequenza 1b2e1d63ff sta per i primi 10 caratteri del commit che si vuol referenziare tramite questo tag. Puoi ottenere l'id della commit tramite
git log
puoi anche utilizzare meno caratteri per l'id della commit, basta che sia unico.

sostituire i cambiamenti locali
Nel caso tu abbia fatto qualcosa di sbagliato (ma non capita mai, sicuro ;) puoi sostituire i cambiamenti fatti in locale con il comando
git checkout -- <nomedelfile>
questo rimpiazza le modifiche nell'albero di lavoro con l'ultimo contenuto presente in HEAD. I cambiamenti fatti ed aggiunti all'index, così come i nuovi files, verranno mantenuti.

Se vuoi in alternativa eliminare tutti i cambiamenti e commits fatti in locale, recupera l'ultima versione dal server e fai puntare il tuo master branch a quella versione in questo modo
git fetch origin
git reset --hard origin/master

suggerimenti utili
GUI (Interfaccia utente grafica) per git disponibile di default
gitk
colora gli output di git
git config color.ui true
mostra il log in una riga per commit
git config format.pretty oneline
utilizza l'aggiunta interattiva
git add -i

I comandi Git semplificano il lavoro
Il sistema di controllo di versione Git è uno strumento importante per tutti gli sviluppatori e le sviluppatrici. Ottimizza il flusso di lavoro per i team sia di piccole sia di grandi dimensioni che desiderano lavorare contemporaneamente a uno stesso progetto, garantendone la sicurezza e la stabilità necessarie. Per i progetti di sviluppo che comprendono diverse persone, reparti e repository, Git gioca un ruolo importante in quanto aiuta tutti i soggetti coinvolti a mantenere la visione d’insieme.
Per poter lavorare correttamente con questo sistema è però indispensabile l’utilizzo dei comandi Git, in quanto consentono di ottimizzare ogni singolo passaggio di lavoro. Di seguito vi presentiamo i comandi Git più importanti.
Prima di iniziare a lavorare con Git
Prima di avviare un nuovo progetto dovreste per prima cosa verificare se avete già installato Git e, in caso affermativo, quale versione è in uso. Il comando da utilizzare è il seguente:
git --version
Se non compare nessun risultato, significa che dovete installare Git manualmente. In Linux potete ricorrere al gestore di pacchetti. Sul Mac l’installazione avviene tramite il terminale, mentre su Windows dovete scaricare Git manualmente e avviare il programma d’installazione.
Per lanciare un nuovo progetto con Git, dovete aprire la cartella desiderata nel terminale e creare un nuovo repository con il seguente comando:
git init
Se desiderate copiare un repository precedentemente creato o eliminato e aggiungerlo nella cartella dovete servirvi del comando Clone in Git:
git clone /percorso/locale/Repository/percorso/destinazione
git clone https://esempio.it/user/Repository.git
Se avete già creato una chiave SSH, potete utilizzare anche il seguente comando:
git clone utente@server:/pfad.git
Configurare nome ed e-mail con comandi Git
Per poter lavorare a un progetto avete bisogno di un nome utente e un indirizzo e-mail valido. Per farlo utilizzate il seguente comando Git.

Configurate il vostro nome utente:
git config --global user.name "Nome di esempio"
Verificate che il nome utente appena creato sia corretto:
git config --global user.name
Associatevi il vostro indirizzo e-mail:
git config --global user.email "indirizzoemail@esempio.it"
Verificate l’indirizzo e-mail corrispondente così:
git config --global user.email

Visualizzate un riepilogo di tutti i dati con un solo comando:
git config --global --list
Resoconto e modifiche
Alcuni importanti comandi Git vi facilitano il lavoro nel e con il repository. Per ottenere una visione d’insieme del repository avete a disposizione i seguenti comandi:
git close esempiogit@esempio.it:Repository.get

Per un resoconto dello stato locale e delle modifiche non ancora applicate, invece, utilizzate questo:
git status

Le modifiche sono evidenziate in rosso.
Potete verificare le differenze tra il commit in corso e la versione precedente con il comando git diff:
git diff HEAD

I comandi Git per i repository
Per salvare soltanto le modifiche nel repository locale, avete bisogno dei seguenti comandi Git.
Aggiungete tutti i dati nuovi, modificati o cancellati con questo comando:
git add

Se, invece, volete applicare soltanto alcune specifiche modifiche al vostro commit dovete aggiungere al comando anche le parentesi quadre:
git add [ file_1 file_2 file_3 | file-pattern ]
Convalidate git commit nel modo seguente:
git commit

Se volete anche fornire delle spiegazioni in relazione a un passaggio di lavoro, potete farlo in questo modo:
git commit -m "Qui scrivete il vostro messaggio"
Con git log potete visualizzare i commit attualmente presenti nel vostro repository locale:
git log

I comandi Git per i rami
I seguenti comandi Git servono per lavorare con i rami, o in inglese “branch”.
Per elencare tutti i rami:
git branch
Per le informazioni sui vari rami:
git fetch
Per elencare tutti i rami nel repository locale:
git branch -a
Per creare nuovi rami:
git branch nome-nuovo-branch
Per passare a un ramo specifico con git checkout:
git checkout nome-altro-ramo
Per creare un nuovo ramo e passare direttamente a quest’ultimo:
git checkout -b nuovo-ramo
Per spostare un nuovo ramo dal repository locale al repository del commit:
git push -i nome-remoto nuovo-ramo
Per eliminare un ramo nel repository locale, ammesso che contenga esclusivamente modifiche già applicate:
git branch -d nome-ramo
Per aggiungere le modifiche da un ramo a quello attualmente in uso:
git merge nome-altro-ramo
Per recuperare le modifiche da un repository eliminato con il comando git pull:
git pull altro-ramo
I comandi Git per i tag
Se siete soliti usare i tag, questi comandi Git vi semplificano il lavoro.
Per elencare tutti i tag:
git tag
Per aprire tutti i tag dal repository del commit per il vostro repository locale:
git fetch --tags
Per visualizzare un tag specifico:
git show nome-tag
Per spostare un tag specifico nel repository del commit con il comando git push:
git push nome-remoto nome-tag
Per spostare tutti i tag nel repository del commit:
git push nome-remoto --tags
Per eliminare un tag nel repository locale:
git tag -d nome-tag

