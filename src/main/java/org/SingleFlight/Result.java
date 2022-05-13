package org.SingleFlight;


import lombok.Data;

@Data
public class Result<V> {
    public enum RESULT_TYPE{
        CONSUMER("CONSUMER"),
        CONSUMER_TIME_OUT("CONSUMER_TIME_OUT"),
        PRODUCER("PRODUCER"),
//        PRODUCER_TIME_UP("PRODUCER_TIME_UP"),
        ;
        public String type;
        RESULT_TYPE(String type) {
            this.type = type;
        }
    }
    V val;
    RESULT_TYPE type;
    Result(V v, RESULT_TYPE type) {
        this.val = v;
        this.type = type;
    }

}
