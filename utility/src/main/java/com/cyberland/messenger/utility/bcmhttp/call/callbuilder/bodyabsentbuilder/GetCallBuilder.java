package com.bcm.messenger.utility.bcmhttp.call.callbuilder.bodyabsentbuilder;

import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp;
import com.bcm.messenger.utility.bcmhttp.utils.config.RequestConst;

import okhttp3.OkHttpClient;

public class GetCallBuilder extends BodyAbsentCallBuilder {
    public GetCallBuilder(OkHttpClient client, BaseHttp service) {
        super(client, service);
    }

    @Override
    protected String method() {
        return RequestConst.METHOD.GET;
    }

}
