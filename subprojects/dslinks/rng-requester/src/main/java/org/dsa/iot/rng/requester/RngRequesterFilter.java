package org.dsa.iot.rng.requester;

import net.engio.mbassy.listener.IMessageFilter;
import net.engio.mbassy.subscription.SubscriptionContext;

import org.dsa.iot.dslink.events.ResponseEvent;
import org.dsa.iot.dslink.requester.responses.ListResponse;
import org.dsa.iot.dslink.requester.responses.SubscriptionResponse;

/**
 * Filter for @Handler annotation in Main
 * 
 * @author pshvets
 *
 */
public class RngRequesterFilter {

    public final static class SubscriptionResponseFilter implements
            IMessageFilter<ResponseEvent> {
        @Override
        public boolean accepts(ResponseEvent message,
                SubscriptionContext context) {
            if (message.getResponse() instanceof SubscriptionResponse) {
                return true;
            }
            return false;
        }
    }

    public final static class ListResponseFilter implements
            IMessageFilter<ResponseEvent> {
        @Override
        public boolean accepts(ResponseEvent message,
                SubscriptionContext context) {
            if (message.getResponse() instanceof ListResponse) {
                return true;
            }
            return false;
        }
    }
}
