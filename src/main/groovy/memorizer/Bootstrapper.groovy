package memorizer

import print.color.Ansi
import print.color.ColoredPrinter
import print.color.ColoredPrinterWIN
import org.apache.commons.lang3.SystemUtils

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

config = [:]
scanner = null
printer = null
iterator = null
lastWord = null
lastComboStreak = 0
items = [] as LinkedList

class Item {
    String word
    String translation
    int comboStreak

    String toCsvRow() {
        return "$word;$translation;$comboStreak\n"
    }
}

def init() {
    def cli = new CliBuilder(usage: 'memo.groovy -[options]', header: 'Options:')
    cli.with {
        h longOpt: 'help', 'Show usage information'
        s longOpt: 'source', args: 1, argName: 'file', 'Use given file for words database. Default is ./file.csv'
        t longOpt: 'threshold', args: 1, argName: 'integer', 'Set the threshold to the given number. Default is 8'
        r longOpt: 'ratio', args: 1, argName: 'double', 'Set word/translation test ratio. Default is 0.5'
    }
    def options = cli.parse(args)

    if (options.h) {
        cli.usage()
        System.exit(0)
    }

    config.source = options.s ?: 'file.csv'
    config.threshold = options.t ?: 10
    config.ratio = options.r ?: 0.5

    printer = (SystemUtils.IS_OS_WINDOWS) ?
            new ColoredPrinterWIN.Builder(1, false).build() :
            new ColoredPrinter.Builder(1, false).build()

    scanner = new Scanner(System.in)
}

def start() {
    def repeat = true
    println 'Hello!'
    println "Press Enter for next word. Type 'help' to list available commands"
    while (repeat) {
        def input = scanner.nextLine()
        switch (input) {
            case 'quit':
                repeat = false
                break
            case '':
                def item = findNextWord()
                if (item)
                    testWord(item)
                else {
                    println 'No words left. Exiting...'
                    repeat = false
                }
                break
            case 'help':
                printHelp()
                break
            case 'add':
                add()
                break
            case 'reset':
                reset()
                break
            case 'delete':
                delete()
                break
            case 'correct':
                correct()
                break
            default:
                println 'Unrecognized input'
        }
    }
    println 'Goodbye!'
}

def add() {
    println 'Enter a new word: '
    String word = scanner.nextLine()
    println 'Enter translation: '
    String translation = scanner.nextLine()
    iterator.add(new Item(word: word, translation: translation, comboStreak: 0))
    println 'Done'
}

def reset() {
    if (iterator.hasPrevious()) {
        item = iterator.previous()
        item.comboStreak = 0
        println item.word
    }
    iterator.next()
    println ""
}

def delete() {
    iterator.previous()
    iterator.remove()
    iterator.next()
}

def correct() {
    if (lastComboStreak) {
        lastWord.comboStreak = lastComboStreak
        lastComboStreak = 0
        println "Corrected. New combo streak is ${lastWord.comboStreak}"
    } else {
        println "Previous word was correct, already corrected or doesn't exist"
    }

}

def findNextWord() {
    while (iterator.hasNext()) {
        def item = iterator.next()
        if (item.comboStreak < config.threshold) {
            return item
        }
    }
    return null
}

def testWord(Item item) {
    if (getRandomBoolean()) {
        print 'Insert translation for '
        printColored(item.word, Ansi.FColor.YELLOW)
        print ': '
        if (scanner.nextLine().toLowerCase() == item.translation.toLowerCase()) {
            lastComboStreak = 0
            printCorrect(++item.comboStreak)
        } else {
            lastWord = item
            lastComboStreak = item.comboStreak + 1
            item.comboStreak = 0
            printIncorrect(item.translation)
        }
    } else {
        print 'Insert original for '
        printColored(item.translation, Ansi.FColor.CYAN)
        print ': '
        if (scanner.nextLine().toLowerCase() == item.word.toLowerCase()) {
            lastComboStreak = 0
            printCorrect(++item.comboStreak)
        } else {
            lastWord = item
            lastComboStreak = item.comboStreak + 1
            item.comboStreak = 0
            printIncorrect(item.word)
        }
    }
}

def printHelp() {
    println '''\
    [enter] - next word
    add     - add a new word
    delete  - delete the last word'
    correct - restore the combo streak of the last word as if it was correct'
    reset   - reset combo streak of the last word'
    help    - print this help'
    quit    - terminate the application'''
}

def printColored(def text, Ansi.FColor color) {
    printer.setForegroundColor(color)
    printer.print(text)
    printer.clear()
}

def printCorrect(comboStreak) {
    printColored('Correct ', Ansi.FColor.GREEN)
    println 'Combo streak is now ' + "$comboStreak"
}

def printIncorrect(answer) {
    printColored('Wrong ', Ansi.FColor.RED)
    println 'Correct answer was: ' + "$answer" + '. Combo streak is reset to zero'
}

def getRandomBoolean() {
    return Math.random() < 0.5;
}

def loadWordsbase() {
    print 'Reading words database... '
    File file = new File((String) config.source)
    if (!file.exists()) {
        println "Can't find the file $config.source. Type memo.groovy help for help. Exiting..."
        System.exit(0)
    }
    file.eachLine { line ->
        try {
            def tokenized = line.split(';')
            items << new Item(
                    word: tokenized[0],
                    translation: tokenized[1],
                    comboStreak: tokenized.size() > 2 ? Integer.parseInt(tokenized[2]) : 0)
        } catch (Exception ignored) {
            print 'Failed parsing string: '
            printColored(line, Ansi.FColor.RED)
            println ', skipping...'
        }
    }
    Collections.shuffle(items)
    iterator = items.listIterator()
    println 'Done!'
}

def saveWordsbase() {
    print 'Saving words database... '
    String filePath = config.source
    Path temporalPath = Paths.get(filePath + '_')
    File file = temporalPath.toFile()
    file.createNewFile()
    file.withWriter { out ->
        items.each { item ->
            out.write item.toCsvRow()
        }
    }
    Path destinationPath = Paths.get(filePath)
    Files.move(temporalPath, destinationPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    println 'Done!'
}

init()
loadWordsbase()
start()
saveWordsbase()

