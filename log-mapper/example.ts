import { LogMapper } from '../src/LogMapper';

const mapper = new LogMapper();

mapper.parseFile('../logs_por_contexto.txt');

mapper.printReport();
