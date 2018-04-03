import java.nio.charset.Charset

appender('STDERR', ConsoleAppender) {
    target = 'System.err'
    encoder(PatternLayoutEncoder) {
        charset = Charset.forName('UTF-8')
        pattern = '%p: %m%n'
    }
}

root(ERROR, ['STDERR'])
