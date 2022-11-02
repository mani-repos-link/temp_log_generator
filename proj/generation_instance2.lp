activity(a).
has_attribute(a,mark).
has_attribute(a,name).

activity(b).
has_attribute(b,grade).
has_attribute(b,mark).
has_attribute(b,name).

value(mark,1..5).

value(grade,1..5).

value(name,x).
value(name,y).
value(name,z).
value(name,v).

template(0,"# Response").
activation(0,A).
activation_condition(0,T):- activation_condition_1(0,T).
activation_condition_1(0,T) :- assigned_value(grade,V,T),V>2.
target(0,B).
correlation_condition(0,T):- correlation_condition_1(0,T).
correlation_condition_1(0,T) :- assigned_value(grade,V,T),V>5.

template(1,"Response").
activation(1,A).
activation_condition(1,T) :- activation_condition_12(1,T).
activation_condition_12(1,T) :- .
activation_condition(1,T) :- activation_condition_34(1,T).
activation_condition_34(1,T) :- .
activation_condition_1(1,T) :- assigned_value(grade,V,T),V>2.
activation_condition_2(1,T) :- assigned_value(name,x,T).
activation_condition_2(1,T) :- assigned_value(name,y,T).
activation_condition_3(1,T) :- assigned_value(grade,V,T),V<2.
activation_condition_4(1,T) :- assigned_value(name,z,T).
activation_condition_4(1,T) :- assigned_value(name,v,T).
target(1,B).
correlation_condition(1,T):- correlation_condition_1(1,T).
correlation_condition_1(1,T) :- assigned_value(grade,V,T),V>5.

